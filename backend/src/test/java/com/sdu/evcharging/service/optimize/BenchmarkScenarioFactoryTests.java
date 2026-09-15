package com.sdu.evcharging.service.optimize;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Random;

import org.junit.jupiter.api.Test;

class BenchmarkScenarioFactoryTests {

    @Test
    void generatedScenarioUsesOneHourSlotsAcrossTheCompleteChargingWindow() {
        BenchmarkScenarioFactory.Scenario scenario =
                BenchmarkScenarioFactory.generateFeasibleScenario(new Random(20260914L), 0);

        int windowHours = (int) Duration.between(
                scenario.constraints().plugInTime(),
                scenario.constraints().departureTime()).toHours();

        assertThat(scenario.priceData()).hasSize(windowHours);
        assertThat(scenario.co2Data()).hasSize(windowHours);
        assertThat(scenario.priceData())
                .zipSatisfy(scenario.co2Data(), (price, co2) ->
                        assertThat(price.timestamp()).isEqualTo(co2.timestamp()));
        assertThat(scenario.priceData())
                .allSatisfy(point -> assertThat(point.timestamp())
                        .isBefore(scenario.constraints().departureTime()));

        for (int slot = 0; slot < scenario.priceData().size(); slot++) {
            assertThat(scenario.priceData().get(slot).timestamp())
                    .isEqualTo(scenario.constraints().plugInTime().plusHours(slot));
        }
    }

    @Test
    void generatedScenarioIsFeasibleAtItsConfiguredPowerLimit() {
        BenchmarkScenarioFactory.Scenario scenario =
                BenchmarkScenarioFactory.generateFeasibleScenario(new Random(42L), 7);

        double availableEnergy = scenario.priceData().size()
                * scenario.constraints().maxChargingPowerKw();

        assertThat(availableEnergy).isGreaterThanOrEqualTo(
                scenario.constraints().energyRequiredKwh());
    }
}
