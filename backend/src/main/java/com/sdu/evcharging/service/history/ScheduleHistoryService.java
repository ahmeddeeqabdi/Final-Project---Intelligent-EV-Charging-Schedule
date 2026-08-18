package com.sdu.evcharging.service.history;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sdu.evcharging.domain.ScheduleResultEntity;
import com.sdu.evcharging.dto.schedule.ChargingSlot;
import com.sdu.evcharging.dto.schedule.ScheduleHistoryItem;
import com.sdu.evcharging.dto.schedule.ScheduleResult;
import com.sdu.evcharging.repository.ScheduleResultRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleHistoryService {

    private final ScheduleResultRepository scheduleResultRepository;

    @Transactional(readOnly = true)
    public List<ScheduleHistoryItem> findRecentForUser(Long userId) {
        return scheduleResultRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ScheduleHistoryService::toHistoryItem)
                .toList();
    }

    private static ScheduleHistoryItem toHistoryItem(ScheduleResultEntity entity) {
        List<ChargingSlot> slots = entity.getSlots().stream()
                .map(slot -> new ChargingSlot(
                        slot.getTimestamp(),
                        slot.getPowerDraw(),
                        slot.getCurrentPrice(),
                        slot.getCurrentCO2()
                ))
                .toList();

        ScheduleResult.DegradedMode degradedMode = new ScheduleResult.DegradedMode(
                entity.isDegradedEnabled(),
                entity.getDegradedReason(),
                entity.getDegradedSource(),
                entity.getDegradedDataAgeHours()
        );

        return new ScheduleHistoryItem(
                entity.getId(),
                entity.getAlgorithm(),
                entity.getTotalPredictedCost(),
                entity.getTotalPredictedEmissions(),
                degradedMode,
                entity.getCreatedAt(),
                slots
        );
    }
}
