package org.thisway.triplog.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.security.dto.request.MemberDetails;
import org.thisway.triplog.dto.response.TripLocationStats;
import org.thisway.triplog.service.StatisticService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/statistics")
public class StatisticController {

    private final StatisticService statisticService;

    @GetMapping("/start-location")
    public ResponseEntity<List<TripLocationStats>> getStartLocationStatBetweenDates(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LocalDateTime startTime = LocalDateTime.of(startDate, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(endDate, LocalTime.MAX);

        return ResponseEntity.status(HttpStatus.OK)
                .body(statisticService.getStartLocationStatBetweenDates(memberDetails.getCompanyId(), startTime, endTime));
    }
}
