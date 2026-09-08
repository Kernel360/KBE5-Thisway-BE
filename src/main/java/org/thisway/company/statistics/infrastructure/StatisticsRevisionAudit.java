package org.thisway.company.statistics.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.company.statistics.domain.Statistics;
import org.thisway.company.statistics.domain.StatisticsRevision;
import org.thisway.company.statistics.domain.StatisticsRevisionRecorder;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class StatisticsRevisionAudit implements StatisticsRevisionRecorder {
    private final EntityManager entities;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void append(Statistics value, String reason) {
        try {
            entities.persist(new StatisticsRevision(value.getCompany().getId(), value.getDate().toLocalDate(),
                    value.getRevision(), reason, LocalDateTime.now(ZoneId.of("Asia/Seoul")),
                    json.writeValueAsString(Snapshot.from(value))));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize statistics revision", failure);
        }
    }

    // Only aggregate values: no device credential, raw coordinate, or personal identifier.
    private record Snapshot(int formulaVersion, long fleetVehicleCount, long gpsObservationCount,
                            long unclosedTripCount, LocalDateTime calculatedAt, Integer powerOnCount,
                            Double averageDailyPowerCount, Integer totalDrivingTime, Integer peakHour,
                            Integer lowHour, Double averageOperationRate, Double[] hourlyRates) {
        static Snapshot from(Statistics value) {
            return new Snapshot(value.getFormulaVersion(), value.getFleetVehicleCount(), value.getGpsObservationCount(),
                    value.getUnclosedTripCount(), value.getCalculatedAt(), value.getPowerOnCount(),
                    value.getAverageDailyPowerCount(), value.getTotalDrivingTime(), value.getPeakHour(),
                    value.getLowHour(), value.getAverageOperationRate(), value.getHourlyRatesArray());
        }
    }
}
