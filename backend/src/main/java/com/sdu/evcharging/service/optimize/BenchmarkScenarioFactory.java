package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;

/**
 * Creates deterministic, realistic hourly charging windows shared by the
 * benchmark endpoint and the regression benchmark test.
 */
final class BenchmarkScenarioFactory {

    private static final double STEP_SIZE_KWH = 0.5;
    private static final int MIN_WINDOW_HOURS = 4;
    private static final int MAX_WINDOW_HOURS = 24;
    private static final double[] MAX_POWER_OPTIONS_KW = {3.7, 7.4, 11.0, 22.0};

    private BenchmarkScenarioFactory() {
    }

    static Scenario generateFeasibleScenario(Random random, int index) {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0).plusHours(index * 36L);

        while (true) {
            int slots = MIN_WINDOW_HOURS + random.nextInt(MAX_WINDOW_HOURS - MIN_WINDOW_HOURS + 1);
            LocalDateTime departure = start.plusHours(slots);

            double maxPower = MAX_POWER_OPTIONS_KW[random.nextInt(MAX_POWER_OPTIONS_KW.length)];
            double battery = 40.0 + random.nextDouble() * 60.0;
            double currentSoc = 5.0 + random.nextDouble() * 65.0;
            double targetSoc = Math.min(100.0, currentSoc + 10.0 + random.nextDouble() * 75.0);

            double weightPrice = random.nextDouble();
            double weightCo2 = random.nextDouble();
            if (weightPrice + weightCo2 < 1e-6) {
                weightPrice = 1.0;
            }

            UserConstraints constraints = new UserConstraints(
                    currentSoc,
                    targetSoc,
                    battery,
                    maxPower,
                    start,
                    departure,
                    random.nextBoolean() ? "DK1" : "DK2",
                    weightPrice,
                    weightCo2);

            List<GridData> priceData = new ArrayList<>(slots);
            List<GridData> co2Data = new ArrayList<>(slots);
            double priceBase = 0.10 + random.nextDouble() * 0.35;
            double co2Base = 80.0 + random.nextDouble() * 320.0;

            for (int hour = 0; hour < slots; hour++) {
                LocalDateTime timestamp = start.plusHours(hour);
                double dailyShape = 0.5 + 0.5 * Math.sin((2.0 * Math.PI * hour) / 24.0);
                double price = Math.max(0.02,
                        priceBase + dailyShape * 0.18 + (random.nextDouble() - 0.5) * 0.10);
                double co2 = Math.max(20.0,
                        co2Base + (1.0 - dailyShape) * 140.0 + (random.nextDouble() - 0.5) * 120.0);
                priceData.add(new GridData(timestamp, price));
                co2Data.add(new GridData(timestamp, co2));
            }

            int maxStepsPerSlot = (int) Math.floor((maxPower / STEP_SIZE_KWH) + 1e-9);
            int totalSteps = (int) Math.ceil((constraints.energyRequiredKwh() / STEP_SIZE_KWH) - 1e-9);
            if (totalSteps > 0 && (long) maxStepsPerSlot * slots >= totalSteps) {
                return new Scenario(constraints, priceData, co2Data);
            }
        }
    }

    record Scenario(UserConstraints constraints, List<GridData> priceData, List<GridData> co2Data) {
    }
}
