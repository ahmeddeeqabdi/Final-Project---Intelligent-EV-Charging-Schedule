package com.sdu.evcharging.domain.strategy;

import java.time.LocalDateTime;

public record GridData(
        LocalDateTime timestamp,
        double value
) {}
