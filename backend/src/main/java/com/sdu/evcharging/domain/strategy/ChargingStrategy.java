package com.sdu.evcharging.domain.strategy;

import java.util.List;

import com.sdu.evcharging.dto.schedule.ScheduleResult;

/**
 * Strategy interface for multi-objective EV charging optimizers.
 */
public interface ChargingStrategy {

    /**
     * Solves the charging schedule using price and CO2 time-series.
     *
     * @param constraints User charging constraints.
     * @param priceData Hourly price signal.
     * @param co2Data Hourly CO2 signal.
     * @return Schedule result with slots and aggregated totals.
     */
    ScheduleResult solve(UserConstraints constraints, List<GridData> priceData, List<GridData> co2Data);
}
