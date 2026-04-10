package com.sdu.evcharging.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "schedule_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "algorithm", nullable = false, length = 32)
    private String algorithm;

    @Column(name = "total_predicted_cost", nullable = false)
    private double totalPredictedCost;

    @Column(name = "total_predicted_emissions", nullable = false)
    private double totalPredictedEmissions;

    @Column(name = "degraded_enabled", nullable = false)
    private boolean degradedEnabled;

    @Column(name = "degraded_reason")
    private String degradedReason;

    @Column(name = "degraded_source", nullable = false)
    private String degradedSource;

    @Column(name = "degraded_data_age_hours")
    private Long degradedDataAgeHours;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "scheduleResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ScheduleSlotEntity> slots = new ArrayList<>();

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public void setSlots(List<ScheduleSlotEntity> slots) {
        this.slots.clear();
        if (slots == null) {
            return;
        }
        for (ScheduleSlotEntity slot : slots) {
            slot.setScheduleResult(this);
            this.slots.add(slot);
        }
    }
}
