package com.sdu.evcharging.service.optimize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NormalizationUtility {

    private static final double EPSILON = 1e-9;

    private NormalizationUtility() {
    }

    public static List<Double> minMaxNormalize(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        double min = Collections.min(values);
        double max = Collections.max(values);

        List<Double> normalized = new ArrayList<>(values.size());
        for (double value : values) {
            normalized.add((value - min) / (max - min + EPSILON));
        }
        return normalized;
    }
}
