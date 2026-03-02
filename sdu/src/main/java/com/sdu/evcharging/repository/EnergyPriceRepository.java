package com.sdu.evcharging.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sdu.evcharging.domain.EnergyPrice;

public interface EnergyPriceRepository extends JpaRepository<EnergyPrice, Long> {

    boolean existsByHourUtcAndPriceArea(LocalDateTime hourUtc, String priceArea);

    List<EnergyPrice> findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
            String priceArea,
            LocalDateTime from,
            LocalDateTime to
    );
}