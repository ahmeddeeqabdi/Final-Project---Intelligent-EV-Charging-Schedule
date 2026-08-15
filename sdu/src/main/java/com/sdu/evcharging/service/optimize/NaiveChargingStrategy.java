package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sdu.evcharging.domain.strategy.ChargingStrategy;
import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleResult;

@Component("naive")
public class NaiveChargingStrategy implements ChargingStrategy {

    private static final double ENERGY_TOLERANCE = 1e-3;
    private static final double DEFAULT_CO2_VALUE = 0.0;

    @Override
    public ScheduleResult solve(
            UserConstraints constraints,
            List<GridData> priceData,
            List<GridData> co2Data
    ) {
        if (priceData == null || priceData.isEmpty()) {
            return StrategySupport.emptyResult();
        }

        double energyNeededKwh = constraints.energyRequiredKwh();
        if (energyNeededKwh <= ENERGY_TOLERANCE) {
            return StrategySupport.emptyResult();
        }

        Map<LocalDateTime, Double> co2ByTime = buildCo2ByTime(co2Data);

        List<ChargingSlot> slots = new ArrayList<>();
        double remainingKwh = energyNeededKwh;

        for (GridData price : priceData) {
            if (remainingKwh <= ENERGY_TOLERANCE) {
                break;
            }

            if (!StrategySupport.isWithinWindow(price.timestamp(), constraints)) {
                continue;
            }

            double energyThisSlot = Math.min(constraints.maxChargingPowerKw(), remainingKwh);
            double co2Value = co2ByTime.getOrDefault(price.timestamp(), DEFAULT_CO2_VALUE);

            slots.add(new ChargingSlot(
                    price.timestamp(),
                    energyThisSlot,
                    price.value(),
                    co2Value
            ));

            remainingKwh -= energyThisSlot;
        }

        slots.sort(Comparator.comparing(ChargingSlot::timestamp));

        double totalCost = calculateTotalCost(slots);
        double totalEmissions = calculateTotalEmissions(slots);

        return new ScheduleResult(slots, totalCost, totalEmissions);
    }

    private static Map<LocalDateTime, Double> buildCo2ByTime(List<GridData> co2Data) {
        Map<LocalDateTime, Double> co2ByTime = new HashMap<>();
        if (co2Data == null) {
            return co2ByTime;
        }

        for (GridData data : co2Data) {
            co2ByTime.putIfAbsent(data.timestamp(), data.value());
        }
        return co2ByTime;
    }

    private static double calculateTotalCost(List<ChargingSlot> slots) {
        double cost = 0.0;
        for (ChargingSlot slot : slots) {
            cost += slot.powerDraw() * slot.currentPrice();
        }
        return cost;
    }

    private static double calculateTotalEmissions(List<ChargingSlot> slots) {
        double emissions = 0.0;
        for (ChargingSlot slot : slots) {
            emissions += slot.powerDraw() * slot.currentCO2();
        }
        return emissions;
    }
}
