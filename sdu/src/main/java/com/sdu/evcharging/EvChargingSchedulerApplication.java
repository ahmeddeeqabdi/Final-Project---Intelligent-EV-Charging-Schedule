package com.sdu.evcharging;

import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.sdu.evcharging.service.ingest.EnergyDataIngestService;

@SpringBootApplication
@EnableScheduling
public class EvChargingSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvChargingSchedulerApplication.class, args);
    }

    @Bean
    public CommandLineRunner testIngest(EnergyDataIngestService ingestService) {
        return args -> {
            System.out.println("--- TEST: FETCHING LIVE DATA FROM ENERGI DATA SERVICE ---");
            
            try {
                ingestService.fetchSpotPrices(LocalDate.of(2024, 2, 1), "DK2");
                System.out.println("--- TEST COMPLETE: CHECK YOUR LOGS ABOVE ---");
            } catch (Exception e) {
                System.err.println("--- TEST FAILED: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}