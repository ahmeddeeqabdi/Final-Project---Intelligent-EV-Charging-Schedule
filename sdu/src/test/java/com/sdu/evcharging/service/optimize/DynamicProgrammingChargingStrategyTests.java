package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ScheduleResult;

class DynamicProgrammingChargingStrategyTests {

    @Test
    void solve_ProducesExactEnergyAndRespectsWindow() {
        DynamicProgrammingChargingStrategy optimizer = new DynamicProgrammingChargingStrategy();

        LocalDateTime t0 = LocalDateTime.of(2026, 4, 11, 8, 0);
        LocalDateTime t1 = t0.plusHours(1);
        LocalDateTime t2 = t0.plusHours(2);
        LocalDateTime t3 = t0.plusHours(3);
        LocalDateTime t4 = t0.plusHours(4);

        UserConstraints constraints = new UserConstraints(
                20.0,
                60.0,
                50.0,
                7.0,
                t1,
                t4.plusHours(1),
                "DK2",
                0.5,
                0.5
        );

        List<GridData> prices = List.of(
                new GridData(t0, 0.90),
                new GridData(t1, 0.35),
                new GridData(t2, 0.15),
                new GridData(t3, 0.60),
                new GridData(t4, 0.25)
        );

        List<GridData> co2 = List.of(
                new GridData(t0, 350.0),
                new GridData(t1, 320.0),
                new GridData(t2, 180.0),
                new GridData(t3, 120.0),
                new GridData(t4, 210.0)
        );

        ScheduleResult result = optimizer.solve(constraints, prices, co2);

        double scheduledEnergy = result.slots().stream().mapToDouble(slot -> slot.powerDraw()).sum();
        assertEquals(constraints.energyRequiredKwh(), scheduledEnergy, 1e-9);
        assertTrue(result.slots().stream().allMatch(slot -> !slot.timestamp().isBefore(t1)));
        assertTrue(result.slots().stream().allMatch(slot -> slot.timestamp().isBefore(t4.plusHours(1))));
        assertTrue(result.slots().stream().allMatch(slot -> slot.powerDraw() <= constraints.maxChargingPowerKw() + 1e-9));
    }

    @Test
    void solve_WhenCo2WeightDominates_SelectsLowerCo2Slot() {
        DynamicProgrammingChargingStrategy optimizer = new DynamicProgrammingChargingStrategy();

        LocalDateTime t1 = LocalDateTime.of(2026, 4, 11, 10, 0);
        LocalDateTime t2 = t1.plusHours(1);

        UserConstraints constraints = new UserConstraints(
                0.0,
                50.0,
                10.0,
                5.0,
                t1,
                t2.plusHours(1),
                "DK1",
                0.0,
                1.0
        );

        List<GridData> prices = List.of(
                new GridData(t1, 0.10),
                new GridData(t2, 1.00)
        );

        List<GridData> co2 = List.of(
                new GridData(t1, 500.0),
                new GridData(t2, 100.0)
        );

        ScheduleResult result = optimizer.solve(constraints, prices, co2);

        assertEquals(1, result.slots().size());
        assertEquals(t2, result.slots().get(0).timestamp());
        assertEquals(5.0, result.slots().get(0).powerDraw(), 1e-9);
    }

    @Test
    void solve_RoundsEnergyWhenNotRepresentableByStepSize() {
        DynamicProgrammingChargingStrategy optimizer = new DynamicProgrammingChargingStrategy();

        LocalDateTime t1 = LocalDateTime.of(2026, 4, 11, 10, 0);
        UserConstraints constraints = new UserConstraints(
                0.0,
                13.0,
                10.0,
                7.0,
                t1,
                t1.plusHours(5),
                "DK1",
                0.5,
                0.5
        );

        List<GridData> prices = List.of(
                new GridData(t1, 0.2),
                new GridData(t1.plusHours(1), 0.3),
                new GridData(t1.plusHours(2), 0.4),
                new GridData(t1.plusHours(3), 0.5),
                new GridData(t1.plusHours(4), 0.6)
        );

        ScheduleResult result = optimizer.solve(constraints, prices, List.of());
        double scheduledEnergy = result.slots().stream().mapToDouble(slot -> slot.powerDraw()).sum();
        assertEquals(1.5, scheduledEnergy, 1e-9);
    }

    @Test
    void solve_ThrowsWhenStateSpaceExceedsSafetyLimit() {
        DynamicProgrammingChargingStrategy optimizer = new DynamicProgrammingChargingStrategy();

        LocalDateTime start = LocalDateTime.of(2026, 4, 11, 0, 0);
        List<GridData> prices = IntStream.range(0, 500)
                .mapToObj(i -> new GridData(start.plusHours(i), 0.2 + (i % 10) * 0.01))
                .toList();

        UserConstraints constraints = new UserConstraints(
                0.0,
                100.0,
                3000.0,
                7.0,
                start,
                start.plusHours(500),
                "DK2",
                1.0,
                0.0
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> optimizer.solve(constraints, prices, List.of()));

        assertTrue(exception.getMessage().contains("state space too large"));
    }

    @Test
    void solve_HasObjectiveValueLessThanOrEqualToGreedyForPriceOnlyObjective() {
        DynamicProgrammingChargingStrategy optimal = new DynamicProgrammingChargingStrategy();
        GreedyChargingStrategy greedy = new GreedyChargingStrategy();

        LocalDateTime t1 = LocalDateTime.of(2026, 4, 11, 12, 0);
        LocalDateTime t2 = t1.plusHours(1);
        LocalDateTime t3 = t1.plusHours(2);
        LocalDateTime t4 = t1.plusHours(3);

        UserConstraints constraints = new UserConstraints(
                20.0,
                60.0,
                50.0,
                7.0,
                t1,
                t4.plusHours(1),
                "DK1",
                1.0,
                0.0
        );

        List<GridData> prices = List.of(
                new GridData(t1, 0.45),
                new GridData(t2, 0.20),
                new GridData(t3, 0.70),
                new GridData(t4, 0.10)
        );

        List<GridData> co2 = List.of(
                new GridData(t1, 200.0),
                new GridData(t2, 200.0),
                new GridData(t3, 200.0),
                new GridData(t4, 200.0)
        );

        ScheduleResult optimalResult = optimal.solve(constraints, prices, co2);
        ScheduleResult greedyResult = greedy.solve(constraints, prices, co2);

        DynamicProgrammingChargingStrategy.RealWorldCostBreakdown optimalBreakdown =
                DynamicProgrammingChargingStrategy.calculateRealWorldCost(
                        optimalResult.slots(),
                        constraints,
                        prices,
                        co2);
        DynamicProgrammingChargingStrategy.RealWorldCostBreakdown greedyBreakdown =
                DynamicProgrammingChargingStrategy.calculateRealWorldCost(
                        greedyResult.slots(),
                        constraints,
                        prices,
                        co2);

        assertTrue(optimalBreakdown.totalRealWorldCost() <= greedyBreakdown.totalRealWorldCost() + 1e-9);
    }
}