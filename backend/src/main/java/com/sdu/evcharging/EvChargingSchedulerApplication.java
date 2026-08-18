package com.sdu.evcharging;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.sdu.evcharging.service.ingest.GridDataSyncService;

@SpringBootApplication
@EnableScheduling
public class EvChargingSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvChargingSchedulerApplication.class, args);
    }

    @Bean
    public CommandLineRunner initialDataSync(GridDataSyncService gridDataSyncService) {
        return args -> {
            gridDataSyncService.syncTodayAndTomorrowAllZones("startup");
        };
    }
}