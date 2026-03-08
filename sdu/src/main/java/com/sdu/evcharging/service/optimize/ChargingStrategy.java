package com.sdu.evcharging.service.optimize;

import java.util.List;

import com.sdu.evcharging.domain.EnergyPrice;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleRequest;

/**
 * Strategy interface for EV charging optimizers.
 * Implementations: NaiveScheduler, GreedyScheduler, DPScheduler.
 */
public interface ChargingStrategy {

    /**
     * Produces an ordered list of hourly charging slots that satisfies
     * the user's energy requirement within their time window.
     *
     * @param request        User constraints (SoC, departure time, etc.)
     * @param availablePrices Ordered price slots within the charging window
     * @return List of charging slots to execute
     */
    List<ChargingSlot> schedule(ScheduleRequest request, List<EnergyPrice> availablePrices);

    /** Human-readable algorithm name for logging and API responses. */
    String name();
}