package org.thisway.triplog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.triplog.dto.response.TripLocationStats;
import org.thisway.triplog.repository.TripLogRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticService {
    private final TripLogRepository tripLogRepository;

    public List<TripLocationStats> getStartLocationStatBetweenDates(Long companyId, LocalDateTime startTime, LocalDateTime endTime) {
        return tripLogRepository.countGroupedByOnAddr(companyId, startTime, endTime);
    }
}
