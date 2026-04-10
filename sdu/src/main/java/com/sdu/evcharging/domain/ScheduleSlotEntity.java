package com.sdu.evcharging.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "schedule_slots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleSlotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "schedule_result_id", nullable = false)
    private ScheduleResultEntity scheduleResult;

    @Column(name = "slot_timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "power_draw", nullable = false)
    private double powerDraw;

    @Column(name = "current_price", nullable = false)
    private double currentPrice;

    @Column(name = "current_co2", nullable = false)
    private double currentCO2;
}
