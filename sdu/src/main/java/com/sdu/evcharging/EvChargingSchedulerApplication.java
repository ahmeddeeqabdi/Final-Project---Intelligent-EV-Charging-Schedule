package com.sdu.evcharging;

import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.sdu.evcharging.service.ingest.DataSyncService;

@SpringBootApplication
@EnableScheduling
public class EvChargingSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvChargingSchedulerApplication.class, args);
    }

    @Bean
    public CommandLineRunner initialDataSync(DataSyncService dataSyncService) {
        return args -> {
            System.out.println("--- STARTUP: Syncing today's and tomorrow's energy data ---");
            LocalDate today    = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            for (String zone : java.util.List.of("DK1", "DK2")) {
                dataSyncService.syncSpotPrices(today, zone);
                dataSyncService.syncCO2Data(today, zone);
                dataSyncService.syncSpotPrices(tomorrow, zone);
                dataSyncService.syncCO2Data(tomorrow, zone);
            }
            System.out.println("--- STARTUP SYNC COMPLETE ---");
        };
    }
}