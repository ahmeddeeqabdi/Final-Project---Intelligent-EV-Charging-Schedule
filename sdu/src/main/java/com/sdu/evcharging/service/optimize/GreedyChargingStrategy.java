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

@Component("greedy")
public class GreedyChargingStrategy implements ChargingStrategy {

    private static final double ENERGY_TOLERANCE = 1e-3;

    @Override
    public ScheduleResult solve(UserConstraints constraints, List<GridData> priceData, List<GridData> co2Data) {
        if (priceData == null || priceData.isEmpty() || constraints.energyRequiredKwh() <= ENERGY_TOLERANCE) {
            return new ScheduleResult(List.of(), 0.0, 0.0);
        }

        Map<LocalDateTime, Double> co2ByTime = new HashMap<>();
        if (co2Data != null) {
            for (GridData data : co2Data) {
                co2ByTime.putIfAbsent(data.timestamp(), data.value());
            }
        }

        double defaultCo2 = 0.0;
        if (co2Data != null && !co2Data.isEmpty()) {
            defaultCo2 = co2Data.stream().mapToDouble(GridData::value).average().orElse(0.0);
        }

        List<CandidateSlot> candidates = new ArrayList<>();
        for (GridData pricePoint : priceData) {
            LocalDateTime timestamp = pricePoint.timestamp();
            if (timestamp.isBefore(constraints.plugInTime()) || !timestamp.isBefore(constraints.departureTime())) {
                continue;
            }
            double co2 = co2ByTime.getOrDefault(timestamp, defaultCo2);
            candidates.add(new CandidateSlot(timestamp, pricePoint.value(), co2));
        }

        if (candidates.isEmpty()) {
            return new ScheduleResult(List.of(), 0.0, 0.0);
        }

        List<Double> prices = candidates.stream().map(CandidateSlot::price).toList();
        List<Double> co2Values = candidates.stream().map(CandidateSlot::co2).toList();
        List<Double> normalizedPrices = NormalizationUtility.minMaxNormalize(prices);
        List<Double> normalizedCo2 = NormalizationUtility.minMaxNormalize(co2Values);

        double weightPrice = Math.max(0.0, constraints.weightPrice());
        double weightCo2 = Math.max(0.0, constraints.weightCO2());
        double weightSum = weightPrice + weightCo2;
        if (weightSum <= 0.0) {
            weightPrice = 0.5;
            weightCo2 = 0.5;
        } else {
            weightPrice /= weightSum;
            weightCo2 /= weightSum;
        }

        for (int i = 0; i < candidates.size(); i++) {
            double score = (weightPrice * normalizedPrices.get(i)) + (weightCo2 * normalizedCo2.get(i));
            candidates.get(i).setScore(score);
        }

        candidates.sort(Comparator.comparingDouble(CandidateSlot::score)
                .thenComparing(CandidateSlot::timestamp));

        double remainingKwh = constraints.energyRequiredKwh();
        List<ChargingSlot> slots = new ArrayList<>();

        for (CandidateSlot candidate : candidates) {
            if (remainingKwh <= ENERGY_TOLERANCE) {
                break;
            }
            double powerDraw = Math.min(constraints.maxChargingPowerKw(), remainingKwh);
            if (powerDraw <= 0.0) {
                continue;
            }
            slots.add(new ChargingSlot(
                    candidate.timestamp(),
                    powerDraw,
                    candidate.price(),
                    candidate.co2()
            ));
            remainingKwh -= powerDraw;
        }

        slots.sort(Comparator.comparing(ChargingSlot::timestamp));

        double totalCost = slots.stream()
                .mapToDouble(slot -> slot.powerDraw() * slot.currentPrice())
                .sum();
        double totalEmissions = slots.stream()
                .mapToDouble(slot -> slot.powerDraw() * slot.currentCO2())
                .sum();

        return new ScheduleResult(slots, totalCost, totalEmissions);
    }

    private static final class CandidateSlot {
        private final LocalDateTime timestamp;
        private final double price;
        private final double co2;
        private double score;

        private CandidateSlot(LocalDateTime timestamp, double price, double co2) {
            this.timestamp = timestamp;
            this.price = price;
            this.co2 = co2;
        }

        private LocalDateTime timestamp() {
            return timestamp;
        }

        private double price() {
            return price;
        }

        private double co2() {
            return co2;
        }

        private double score() {
            return score;
        }

        private void setScore(double score) {
            this.score = score;
        }
    }
}
