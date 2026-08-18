package com.sdu.evcharging.service.optimize;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormalizationUtilityTests {

    @Test
    void minMaxNormalizeScalesIntoZeroOneRange() {
        List<Double> normalized = NormalizationUtility.minMaxNormalize(List.of(10.0, 20.0, 30.0));

        assertEquals(0.0, normalized.get(0), 1e-9);
        assertEquals(0.5, normalized.get(1), 1e-6);
        assertEquals(1.0, normalized.get(2), 1e-6);
    }

    @Test
    void minMaxNormalizeHandlesConstantValues() {
        List<Double> normalized = NormalizationUtility.minMaxNormalize(List.of(7.0, 7.0, 7.0));

        assertEquals(0.0, normalized.get(0), 1e-9);
        assertEquals(0.0, normalized.get(1), 1e-9);
        assertEquals(0.0, normalized.get(2), 1e-9);
    }
}
