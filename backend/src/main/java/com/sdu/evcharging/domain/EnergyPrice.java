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
    name = "energy_prices",
    uniqueConstraints = @UniqueConstraint(columnNames = {"hour_utc", "price_area"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnergyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hour_utc", nullable = false)
    private LocalDateTime hourUtc;

    @Column(name = "price_area", nullable = false, length = 3)
    private String priceArea; 

    
    @Column(name = "price_dkk_per_kwh", nullable = false)
    private Double priceDkkPerKwh;
}