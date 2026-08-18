package com.sdu.evcharging.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sdu.evcharging.domain.ScheduleResultEntity;

public interface ScheduleResultRepository extends JpaRepository<ScheduleResultEntity, Long> {

    List<ScheduleResultEntity> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
