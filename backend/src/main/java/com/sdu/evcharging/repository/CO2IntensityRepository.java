package com.sdu.evcharging.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sdu.evcharging.domain.CO2Intensity;

public interface CO2IntensityRepository extends JpaRepository<CO2Intensity, Long> {

    boolean existsByTimestampUtcAndPriceArea(LocalDateTime timestampUtc, String priceArea);

    List<CO2Intensity> findByPriceAreaAndTimestampUtcBetweenOrderByTimestampUtcAsc(
            String priceArea,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<CO2Intensity> findTopByPriceAreaOrderByTimestampUtcDesc(String priceArea);
}