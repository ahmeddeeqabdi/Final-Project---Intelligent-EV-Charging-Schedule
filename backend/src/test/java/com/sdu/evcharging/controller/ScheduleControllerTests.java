package com.sdu.evcharging.controller;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.sdu.evcharging.dto.schedule.ScheduleRequest;
import com.sdu.evcharging.dto.schedule.ScheduleHistoryItem;
import com.sdu.evcharging.dto.schedule.ScheduleResult;
import com.sdu.evcharging.security.AuthUserPrincipal;
import com.sdu.evcharging.service.history.ScheduleHistoryService;
import com.sdu.evcharging.service.ingest.GridDataSyncService;
import com.sdu.evcharging.service.optimize.SchedulingService;

@ExtendWith(MockitoExtension.class)
class ScheduleControllerTests {

    @Mock
    private SchedulingService schedulingService;

    @Mock
    private ScheduleHistoryService scheduleHistoryService;

    @Mock
    private GridDataSyncService gridDataSyncService;

    private ScheduleController scheduleController;

    @BeforeEach
    void setUp() {
        scheduleController = new ScheduleController(schedulingService, scheduleHistoryService, gridDataSyncService);
    }

    @Test
    void createSchedule_RejectsUnsupportedZone() {
        AuthUserPrincipal principal = new AuthUserPrincipal(7L, "driver@example.com", "hash", "USER");
        ScheduleRequest request = new ScheduleRequest(
                20.0,
                80.0,
                50.0,
                11.0,
                LocalDateTime.of(2026, 3, 13, 10, 0),
                LocalDateTime.of(2026, 3, 13, 15, 0),
                "FR3",
                0.5,
                0.5
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> scheduleController.createSchedule(principal, request, "greedy", true));

        assertEquals("Zone must be DK1 or DK2", exception.getMessage());
        verifyNoInteractions(schedulingService);
    }

    @Test
    void createSchedule_ForwardsRequestToService() {
        AuthUserPrincipal principal = new AuthUserPrincipal(7L, "driver@example.com", "hash", "USER");
        ScheduleRequest request = new ScheduleRequest(
                20.0,
                80.0,
                50.0,
                11.0,
                LocalDateTime.of(2026, 3, 13, 10, 0),
                LocalDateTime.of(2026, 3, 13, 15, 0),
                "DK1",
                0.5,
                0.5
        );
        ScheduleResult result = new ScheduleResult(List.of(), 12.5, 44.0);
        when(schedulingService.createSchedule(request, "optimal", 7L)).thenReturn(result);

        ResponseEntity<ScheduleResult> response = scheduleController.createSchedule(principal, request, "optimal", true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(result, response.getBody());
        verify(schedulingService).createSchedule(request, "optimal", 7L);
    }

    @Test
    void createSchedule_DoesNotPersistComparisonRuns() {
        AuthUserPrincipal principal = new AuthUserPrincipal(7L, "driver@example.com", "hash", "USER");
        ScheduleRequest request = new ScheduleRequest(
                20.0,
                80.0,
                50.0,
                11.0,
                LocalDateTime.of(2026, 3, 13, 10, 0),
                LocalDateTime.of(2026, 3, 13, 15, 0),
                "DK1",
                0.5,
                0.5
        );
        ScheduleResult result = new ScheduleResult(List.of(), 12.5, 44.0);
        when(schedulingService.createSchedule(request, "naive", null)).thenReturn(result);

        ResponseEntity<ScheduleResult> response = scheduleController.createSchedule(principal, request, "naive", false);

        assertSame(result, response.getBody());
        verify(schedulingService).createSchedule(request, "naive", null);
    }

    @Test
    void getHistory_ReturnsAuthenticatedUsersRecentSchedules() {
        AuthUserPrincipal principal = new AuthUserPrincipal(7L, "driver@example.com", "hash", "USER");
        List<ScheduleHistoryItem> history = List.of();
        when(scheduleHistoryService.findRecentForUser(7L)).thenReturn(history);

        ResponseEntity<List<ScheduleHistoryItem>> response = scheduleController.getHistory(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(history, response.getBody());
        verify(scheduleHistoryService).findRecentForUser(7L);
    }
}
