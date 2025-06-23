package org.thisway.statistics.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.thisway.security.dto.request.MemberDetails;
import org.thisway.statistics.dto.response.StatisticResponse;
import org.thisway.statistics.service.StatisticService;
import org.thisway.triplog.dto.response.TripLocationStats;

@Controller
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

    /**
     * 날짜 범위 기반 통계 조회
     * @param memberDetails 인증된 사용자 정보
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 통계 응답 (시동 횟수, 평균 일일 시동 횟수, 총 운전 시간 등)
     */
    @GetMapping
    public ResponseEntity<StatisticResponse> getStatistics(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        StatisticResponse response = statisticService.getStatisticByDateRange(
                memberDetails.getCompanyId(), startDate, endDate
        );
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * 통계 저장 (배치용)
     * @param companyId 회사 ID
     * @param targetDate 저장할 날짜
     * @return 저장 완료 응답
     */
    @PostMapping("/save")
    public ResponseEntity<String> saveStatistics(
            @RequestParam Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate
    ) {
        statisticService.saveStatistics(companyId, targetDate);
        return ResponseEntity.status(HttpStatus.OK).body("통계 저장이 완료되었습니다.");
    }
}
