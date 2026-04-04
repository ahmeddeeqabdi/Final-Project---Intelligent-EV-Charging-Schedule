package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sdu.evcharging.domain.strategy.GridData;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleResult;

class NaiveSchedulerTests {

    @Test
    void solve_ChargesImmediatelyUntilFull() {
        NaiveScheduler scheduler = new NaiveScheduler();

        LocalDateTime t1 = LocalDateTime.of(2026, 3, 13, 10, 0);
        LocalDateTime t2 = t1.plusHours(1);
        LocalDateTime t3 = t1.plusHours(2);
        LocalDateTime t4 = t1.plusHours(3);

        UserConstraints constraints = new UserConstraints(
                20.0, 60.0, 15.0, 7.0, t1, t4.plusHours(1), "DK1", 0.5, 0.5
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

        ScheduleResult result = scheduler.solve(constraints, priceData, co2Data);

        assertEquals(3, result.slots().size());
        
        ChargingSlot slot1 = result.slots().get(0);
        ChargingSlot slot2 = result.slots().get(1);
        ChargingSlot slot3 = result.slots().get(2);

        assertEquals(t1, slot1.timestamp());
        assertEquals(7.0, slot1.powerDraw(), 1e-9);

        assertEquals(t2, slot2.timestamp());
        assertEquals(7.0, slot2.powerDraw(), 1e-9);

        assertEquals(t3, slot3.timestamp());
        assertEquals(1.0, slot3.powerDraw(), 1e-9);

        // Calculate expected cost: (7 * 0.4) + (7 * 0.2) + (1 * 0.3) = 2.8 + 1.4 + 0.3 = 4.5
        assertEquals(4.5, result.totalPredictedCost(), 1e-9);
        // Calculate expected CO2: (7 * 200) + (7 * 300) + (1 * 100) = 1400 + 2100 + 100 = 3600
        assertEquals(3600.0, result.totalPredictedEmissions(), 1e-9);
    }

    @Test
    void solve_EmptyPriceData_ReturnsEmptyResult() {
        NaiveScheduler scheduler = new NaiveScheduler();
        UserConstraints constraints = new UserConstraints(
                20.0, 60.0, 15.0, 7.0, LocalDateTime.now(), LocalDateTime.now().plusHours(5), "DK1", 0.5, 0.5
        );

        ScheduleResult result = scheduler.solve(constraints, List.of(), List.of());

        assertTrue(result.slots().isEmpty());
        assertEquals(0.0, result.totalPredictedCost());
        assertEquals(0.0, result.totalPredictedEmissions());
    }

    @Test
    void solve_ZeroEnergyRequired_ReturnsEmptyResult() {
        NaiveScheduler scheduler = new NaiveScheduler();
        UserConstraints constraints = new UserConstraints(
                20.0, 60.0, 0.0, 7.0, LocalDateTime.now(), LocalDateTime.now().plusHours(5), "DK1", 0.5, 0.5
        );

        List<GridData> priceData = List.of(new GridData(LocalDateTime.now(), 0.40));

        ScheduleResult result = scheduler.solve(constraints, priceData, null);

        assertTrue(result.slots().isEmpty());
    }

    @Test
    void solve_IgnoresSlotsOutsideTimeWindow() {
        NaiveScheduler scheduler = new NaiveScheduler();

        LocalDateTime plugIn = LocalDateTime.of(2026, 3, 13, 10, 0);
        LocalDateTime departure = plugIn.plusHours(2);

        UserConstraints constraints = new UserConstraints(
                20.0, 60.0, 10.0, 7.0, plugIn, departure, "DK1", 0.5, 0.5
        );

        List<GridData> priceData = List.of(
                new GridData(plugIn.minusHours(1), 0.10), // Before plug-in
                new GridData(plugIn, 0.20),               // Valid
                new GridData(plugIn.plusHours(1), 0.30),  // Valid
                new GridData(plugIn.plusHours(2), 0.40)   // After/at departure
        );

        ScheduleResult result = scheduler.solve(constraints, priceData, null);

        assertEquals(2, result.slots().size());
        assertEquals(plugIn, result.slots().get(0).timestamp());
        assertEquals(plugIn.plusHours(1), result.slots().get(1).timestamp());
        assertEquals(7.0, result.slots().get(0).powerDraw(), 1e-9);
        assertEquals(3.0, result.slots().get(1).powerDraw(), 1e-9);
    }
}