package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleResult;

class GreedyChargingStrategyTests {

    @Test
    void solveRanksByWeightedNormalizedScoreAndAllocatesWithPartialFinalSlot() {
        GreedyChargingStrategy strategy = new GreedyChargingStrategy();

        LocalDateTime t1 = LocalDateTime.of(2026, 3, 13, 10, 0);
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
                0.5,
                0.5
        );

        List<GridData> priceData = List.of(
                new GridData(t1, 0.40),
                new GridData(t2, 0.20),
                new GridData(t3, 0.30),
                new GridData(t4, 0.10)
        );

        List<GridData> co2Data = List.of(
                new GridData(t1, 200.0),
                new GridData(t2, 300.0),
                new GridData(t3, 100.0),
                new GridData(t4, 400.0)
        );

        ScheduleResult result = strategy.solve(constraints, priceData, co2Data);

        assertEquals(3, result.slots().size());
        assertFalse(containsSlot(result.slots(), t1));
        assertTrue(containsSlot(result.slots(), t2));
        assertTrue(containsSlot(result.slots(), t3));
        assertTrue(containsSlot(result.slots(), t4));

        ChargingSlot slotT2 = findSlot(result.slots(), t2);
        ChargingSlot slotT3 = findSlot(result.slots(), t3);
        ChargingSlot slotT4 = findSlot(result.slots(), t4);

        assertEquals(7.0, slotT2.powerDraw(), 1e-9);
        assertEquals(7.0, slotT3.powerDraw(), 1e-9);
        assertEquals(6.0, slotT4.powerDraw(), 1e-9);

        assertEquals(4.1, result.totalPredictedCost(), 1e-9);
        assertEquals(5200.0, result.totalPredictedEmissions(), 1e-9);
    }

    @Test
    void solve_WithEmptyPriceData_ReturnsEmptyResult() {
        GreedyChargingStrategy strategy = new GreedyChargingStrategy();
        UserConstraints constraints = new UserConstraints(
                20.0, 60.0, 50.0, 7.0, LocalDateTime.now(), LocalDateTime.now().plusHours(5), "DK1", 0.5, 0.5
        );

        ScheduleResult result = strategy.solve(constraints, List.of(), List.of());

        assertTrue(result.slots().isEmpty());
        assertEquals(0.0, result.totalPredictedCost());
    }

    @Test
    void solve_WithZeroEnergyRequired_ReturnsEmptyResult() {
        GreedyChargingStrategy strategy = new GreedyChargingStrategy();
        UserConstraints constraints = new UserConstraints(
                20.0, 60.0, 0.0, 7.0, LocalDateTime.now(), LocalDateTime.now().plusHours(5), "DK1", 0.5, 0.5
        );

        ScheduleResult result = strategy.solve(constraints, List.of(new GridData(LocalDateTime.now(), 0.5)), List.of());

        assertTrue(result.slots().isEmpty());
    }

    @Test
    void solve_WithNullCo2Data_UsesDefaultZeroAndSchedules() {
        GreedyChargingStrategy strategy = new GreedyChargingStrategy();
        LocalDateTime t1 = LocalDateTime.now();
        UserConstraints constraints = new UserConstraints(
                20.0, 60.0, 5.0, 7.0, t1, t1.plusHours(2), "DK1", 1.0, 0.0
        );

        List<GridData> prices = List.of(new GridData(t1, 0.5));
        
        ScheduleResult result = strategy.solve(constraints, prices, null);

        assertEquals(1, result.slots().size());
        assertEquals(0.0, result.slots().get(0).currentCO2());
        assertEquals(5.0, result.slots().get(0).powerDraw());
    }

    private static boolean containsSlot(List<ChargingSlot> slots, LocalDateTime timestamp) {
        return slots.stream().anyMatch(slot -> slot.timestamp().equals(timestamp));
    }

    private static ChargingSlot findSlot(List<ChargingSlot> slots, LocalDateTime timestamp) {
        return slots.stream().filter(slot -> slot.timestamp().equals(timestamp)).findFirst().orElseThrow();
    }
}
