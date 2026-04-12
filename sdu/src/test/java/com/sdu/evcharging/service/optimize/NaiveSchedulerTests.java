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
        NaiveChargingStrategy scheduler = new NaiveChargingStrategy();

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

        assertEquals(1, result.slots().size());

        ChargingSlot slot1 = result.slots().get(0);
        assertEquals(t1, slot1.timestamp());
        assertEquals(6.0, slot1.powerDraw(), 1e-9);

        assertEquals(2.4, result.totalPredictedCost(), 1e-9);
        assertEquals(1200.0, result.totalPredictedEmissions(), 1e-9);
    }

    @Test
    void solve_EmptyPriceData_ReturnsEmptyResult() {
        NaiveChargingStrategy scheduler = new NaiveChargingStrategy();
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
        NaiveChargingStrategy scheduler = new NaiveChargingStrategy();
        UserConstraints constraints = new UserConstraints(
                20.0, 60.0, 0.0, 7.0, LocalDateTime.now(), LocalDateTime.now().plusHours(5), "DK1", 0.5, 0.5
        );

        List<GridData> priceData = List.of(new GridData(LocalDateTime.now(), 0.40));

        ScheduleResult result = scheduler.solve(constraints, priceData, null);

        assertTrue(result.slots().isEmpty());
    }

    @Test
    void solve_IgnoresSlotsOutsideTimeWindow() {
        NaiveChargingStrategy scheduler = new NaiveChargingStrategy();

        LocalDateTime plugIn = LocalDateTime.of(2026, 3, 13, 10, 0);
        LocalDateTime departure = plugIn.plusHours(2);

        UserConstraints constraints = new UserConstraints(
                20.0, 60.0, 10.0, 7.0, plugIn, departure, "DK1", 0.5, 0.5
        );

        List<GridData> priceData = List.of(
                new GridData(plugIn.minusHours(1), 0.10), 
                new GridData(plugIn, 0.20),               
                new GridData(plugIn.plusHours(1), 0.30),  
                new GridData(plugIn.plusHours(2), 0.40)   
        );

        ScheduleResult result = scheduler.solve(constraints, priceData, null);

                assertEquals(1, result.slots().size());
                assertEquals(plugIn, result.slots().get(0).timestamp());
                assertEquals(4.0, result.slots().get(0).powerDraw(), 1e-9);
    }
}