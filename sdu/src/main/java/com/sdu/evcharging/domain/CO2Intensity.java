package com.sdu.evcharging.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "co2_intensity",
    uniqueConstraints = @UniqueConstraint(columnNames = {"timestamp_utc", "price_area"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CO2Intensity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp_utc", nullable = false)
    private LocalDateTime timestampUtc;

    @Column(name = "price_area", nullable = false, length = 3)
    private String priceArea;

    // Unit: gCO2/kWh
    @Column(name = "g_per_kwh", nullable = false)
    private Double gPerKwh;
}