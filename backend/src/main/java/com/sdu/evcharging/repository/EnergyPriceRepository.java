package com.sdu.evcharging.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sdu.evcharging.domain.EnergyPrice;

public interface EnergyPriceRepository extends JpaRepository<EnergyPrice, Long> {

    boolean existsByHourUtcAndPriceArea(LocalDateTime hourUtc, String priceArea);

    Optional<EnergyPrice> findByHourUtcAndPriceArea(LocalDateTime hourUtc, String priceArea);

    List<EnergyPrice> findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
            String priceArea,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<EnergyPrice> findTopByPriceAreaOrderByHourUtcDesc(String priceArea);
}