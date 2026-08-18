package com.sdu.evcharging.dto.schedule;

import java.time.LocalDateTime;

public record ScheduleRequest(
        double currentSocPercent,     
        double targetSocPercent,      
        double batteryCapacityKwh,     
        double maxChargingPowerKw,     
        LocalDateTime plugInTime,     
        LocalDateTime departureTime,   
        String priceZone,             
        double weightPrice,           
        double weightCO2              
) {}