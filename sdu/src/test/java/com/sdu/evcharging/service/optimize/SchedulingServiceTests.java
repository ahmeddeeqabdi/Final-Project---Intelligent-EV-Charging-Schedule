package com.sdu.evcharging.service.optimize;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sdu.evcharging.domain.CO2Intensity;
import com.sdu.evcharging.domain.EnergyPrice;
import com.sdu.evcharging.domain.strategy.ChargingStrategy;
import com.sdu.evcharging.domain.strategy.UserConstraints;
import com.sdu.evcharging.dto.schedule.ScheduleRequest;
import com.sdu.evcharging.dto.schedule.ScheduleResult;
import com.sdu.evcharging.repository.CO2IntensityRepository;
import com.sdu.evcharging.repository.EnergyPriceRepository;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceTests {

    @Mock
    private EnergyPriceRepository energyPriceRepository;

    @Mock
    private CO2IntensityRepository co2IntensityRepository;

    @Mock
    private Map<String, ChargingStrategy> strategies;

    @Mock
    private ChargingStrategy mockStrategy;

    @InjectMocks
    private SchedulingService schedulingService;

    private ScheduleRequest request;
    private LocalDateTime plugInTime;
    private LocalDateTime departureTime;

    @BeforeEach
    void setUp() {
        plugInTime = LocalDateTime.of(2026, 3, 13, 10, 0);
        departureTime = plugInTime.plusHours(5);

        request = new ScheduleRequest(
                20.0, 80.0, 50.0, 11.0, plugInTime, departureTime, "DK1", 0.5, 0.5
        );
    }

    @Test
    void createSchedule_ValidInput_ReturnsSchedule() {
        EnergyPrice price1 = new EnergyPrice(1L, plugInTime, "DK1", 0.5);
        CO2Intensity co21 = new CO2Intensity(1L, plugInTime, "DK1", 200.0);
        ScheduleResult expectedResult = new ScheduleResult(List.of(), 10.0, 100.0);

        when(energyPriceRepository.findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
                "DK1", plugInTime, departureTime)).thenReturn(List.of(price1));
        when(co2IntensityRepository.findByPriceAreaAndTimestampUtcBetweenOrderByTimestampUtcAsc(
                "DK1", plugInTime, departureTime)).thenReturn(List.of(co21));
        
        when(strategies.get("greedy")).thenReturn(mockStrategy);
        when(mockStrategy.solve(any(UserConstraints.class), any(List.class), any(List.class)))
                .thenReturn(expectedResult);

        ScheduleResult actualResult = schedulingService.createSchedule(request, "greedy");

        assertEquals(expectedResult, actualResult);
        verify(mockStrategy).solve(any(), any(), any());
    }

    @Test
    void createSchedule_NoPrices_ThrowsException() {
        when(energyPriceRepository.findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
                "DK1", plugInTime, departureTime)).thenReturn(List.of());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            schedulingService.createSchedule(request, "greedy");
        });

        assertTrue(exception.getMessage().contains("No price data found"));
    }

    @Test
    void createSchedule_InvalidAlgorithm_FallsBackToNaive() {
        EnergyPrice price1 = new EnergyPrice(1L, plugInTime, "DK1", 0.5);
        ScheduleResult expectedResult = new ScheduleResult(List.of(), 50.0, 200.0);

        when(energyPriceRepository.findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
                "DK1", plugInTime, departureTime)).thenReturn(List.of(price1));
        
        when(strategies.get("non-existent")).thenReturn(null);
        when(strategies.get("naive")).thenReturn(mockStrategy);
        when(mockStrategy.solve(any(UserConstraints.class), any(List.class), any(List.class)))
                .thenReturn(expectedResult);

        ScheduleResult actualResult = schedulingService.createSchedule(request, "non-existent");

        assertEquals(expectedResult, actualResult);
        verify(mockStrategy).solve(any(), any(), any());
    }

    @Test
    void createSchedule_NoStrategiesAvailable_ThrowsException() {
        EnergyPrice price1 = new EnergyPrice(1L, plugInTime, "DK1", 0.5);

        when(energyPriceRepository.findByPriceAreaAndHourUtcBetweenOrderByHourUtcAsc(
                "DK1", plugInTime, departureTime)).thenReturn(List.of(price1));
        
        when(strategies.get("greedy")).thenReturn(null);
        when(strategies.get("naive")).thenReturn(null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            schedulingService.createSchedule(request, "greedy");
        });

        assertTrue(exception.getMessage().contains("No scheduling strategy is registered"));
    }

    private void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}