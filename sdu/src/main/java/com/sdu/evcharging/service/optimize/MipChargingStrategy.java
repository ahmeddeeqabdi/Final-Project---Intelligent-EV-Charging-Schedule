package com.sdu.evcharging.service.optimize;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import com.sdu.evcharging.domain.strategy.ChargingStrategy;
import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleResult;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component("mip")
public class MipChargingStrategy implements ChargingStrategy {

    static {
        Loader.loadNativeLibraries();
    }

    private static final double ENERGY_TOLERANCE = 1e-3;
    private static final double STARTUP_PENALTY = 0.25;
    private static final double SLOT_DURATION_HOURS = 1.0;

    @Override
    public ScheduleResult solve(UserConstraints constraints, List<GridData> priceData, List<GridData> co2Data) {
        Objects.requireNonNull(constraints, "constraints must not be null");

        if (priceData == null || priceData.isEmpty()) {
            return emptyResult();
        }

        double energyRequiredKwh = constraints.energyRequiredKwh();
        if (energyRequiredKwh <= ENERGY_TOLERANCE) {
            return emptyResult();
        }

        LocalDateTime start = constraints.plugInTime();
        LocalDateTime end = constraints.departureTime();

        List<GridData> inWindowPrice = priceData.stream()
                .filter(d -> !d.timestamp().isBefore(start) && d.timestamp().isBefore(end))
                .sorted(Comparator.comparing(GridData::timestamp))
                .toList();

        if (inWindowPrice.isEmpty()) {
            return emptyResult();
        }

        Map<LocalDateTime, Double> co2ByHour = new HashMap<>();
        if (co2Data != null) {
            for (GridData d : co2Data) {
                co2ByHour.put(d.timestamp(), d.value());
            }
        }

        List<Double> prices = inWindowPrice.stream().map(GridData::value).toList();
        List<Double> co2Values = inWindowPrice.stream()
                .map(d -> co2ByHour.getOrDefault(d.timestamp(), 0.0))
                .toList();

        List<Double> normalizedPrices = NormalizationUtility.minMaxNormalize(prices);
        List<Double> normalizedCo2 = NormalizationUtility.minMaxNormalize(co2Values);

        double sumWeights = constraints.weightPrice() + constraints.weightCO2();
        double wPrice = sumWeights > 0 ? constraints.weightPrice() / sumWeights : 0.5;
        double wCo2 = sumWeights > 0 ? constraints.weightCO2() / sumWeights : 0.5;

        // Create the linear solver with the SCIP backend.
        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            log.error("Could not create SCIP solver");
            return emptyResult();
        }
        
        int n = inWindowPrice.size();
        double pMax = constraints.maxChargingPowerKw();

        if (pMax * n * SLOT_DURATION_HOURS < energyRequiredKwh) {
            throw new IllegalArgumentException("Charging window is infeasible for the required energy and max power.");
        }

        // Variables
        // x[i] = power draw in slot i (continuous)
        MPVariable[] x = new MPVariable[n];
        // y[i] = 1 if charging in slot i, 0 otherwise (binary)
        MPVariable[] y = new MPVariable[n];
        // z[i] = 1 if charging started at slot i, 0 otherwise (binary)
        MPVariable[] z = new MPVariable[n];

        for (int i = 0; i < n; ++i) {
            x[i] = solver.makeNumVar(0.0, pMax, "x_" + i);
            y[i] = solver.makeIntVar(0.0, 1.0, "y_" + i);
            z[i] = solver.makeIntVar(0.0, 1.0, "z_" + i);
        }

        // Constraints
        // 1. Total energy matches required energy
        MPConstraint energyConstraint = solver.makeConstraint(energyRequiredKwh, energyRequiredKwh, "energy_target");
        for (int i = 0; i < n; ++i) {
            energyConstraint.setCoefficient(x[i], SLOT_DURATION_HOURS);
        }

        // 2. Big-M constraint for power: x[i] <= Pmax * y[i]
        // Which translates to: x[i] - Pmax * y[i] <= 0
        for (int i = 0; i < n; ++i) {
            MPConstraint powerLimit = solver.makeConstraint(-MPSolver.infinity(), 0.0, "power_limit_" + i);
            powerLimit.setCoefficient(x[i], 1.0);
            powerLimit.setCoefficient(y[i], -pMax);
        }

        // 3. Startup logic: z[i] >= y[i] - y[i-1]
        // For i = 0, y[-1] is considered 0 -> z[0] >= y[0] -> z[0] - y[0] >= 0
        MPConstraint startup0 = solver.makeConstraint(0.0, MPSolver.infinity(), "startup_0");
        startup0.setCoefficient(z[0], 1.0);
        startup0.setCoefficient(y[0], -1.0);

        for (int i = 1; i < n; ++i) {
            // z[i] - y[i] + y[i-1] >= 0
            MPConstraint startupI = solver.makeConstraint(0.0, MPSolver.infinity(), "startup_" + i);
            startupI.setCoefficient(z[i], 1.0);
            startupI.setCoefficient(y[i], -1.0);
            startupI.setCoefficient(y[i - 1], 1.0);
        }

        // Objective: minimize cost + emissions + startup penalties
        // Note: the DP penalizes the score (w * price + w * co2). 
        // We will do the same: objective_i = score_i * x_i + STARTUP_PENALTY * z_i
        MPObjective objective = solver.objective();
        for (int i = 0; i < n; ++i) {
            double score = wPrice * normalizedPrices.get(i) + wCo2 * normalizedCo2.get(i);
            objective.setCoefficient(x[i], score);
            objective.setCoefficient(z[i], STARTUP_PENALTY);
        }
        objective.setMinimization();

        final MPSolver.ResultStatus resultStatus = solver.solve();

        if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {
            List<ChargingSlot> slots = new ArrayList<>();
            double totalCost = 0;
            double totalEmissions = 0;

            for (int i = 0; i < n; ++i) {
                double powerDraw = x[i].solutionValue();
                if (powerDraw > ENERGY_TOLERANCE) {
                    double price = prices.get(i);
                    double co2 = co2Values.get(i);
                    slots.add(new ChargingSlot(inWindowPrice.get(i).timestamp(), powerDraw, price, co2));
                    totalCost += powerDraw * price;
                    totalEmissions += powerDraw * co2;
                }
            }
            return new ScheduleResult(slots, totalCost, totalEmissions);
        } else {
            throw new IllegalArgumentException("No feasible MIP schedule found for required energy within constraints.");
        }
    }

    private static ScheduleResult emptyResult() {
        return new ScheduleResult(List.of(), 0.0, 0.0);
    }
}