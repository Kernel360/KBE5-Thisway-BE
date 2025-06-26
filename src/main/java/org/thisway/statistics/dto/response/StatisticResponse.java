package org.thisway.statistics.dto.response;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import org.thisway.statistics.entity.Statistics;

public record StatisticResponse(
    Long companyId,
    String date,
    Integer powerOnCount,
    Double averageDailyPowerCount,
    Integer totalDrivingTime,
    Integer peakHour,
    Integer lowHour,
    Double averageOperationRate,
    List<Integer> hours
) {
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  public static StatisticResponse from(Statistics statistics){
    // Statistics 엔티티의 getHourlyRatesArray() 메서드 활용
    List<Integer> hours = Arrays.asList(statistics.getHourlyRatesArray());

    return new StatisticResponse(
        statistics.getCompany().getId(),
        statistics.getDate().format(FORMATTER),
        statistics.getPowerOnCount(),
        statistics.getAverageDailyPowerCount(),
        statistics.getTotalDrivingTime(),
        statistics.getPeakHour(),
        statistics.getLowHour(),
        statistics.getAverageOperationRate(),
        hours
    );
  }

  /**
   * 배열 기반 생성자 (집계된 통계용)
   */
  public static StatisticResponse fromAggregatedData(
      Long companyId, String dateRange, Integer powerOnCount, Double averageDailyPowerCount,
      Integer totalDrivingTime, Integer peakHour, Integer lowHour, Double averageOperationRate,
      List<Integer> hourlyRates) {

    return new StatisticResponse(
        companyId,
        dateRange,
        powerOnCount,
        averageDailyPowerCount,
        totalDrivingTime,
        peakHour,
        lowHour,
        averageOperationRate,
        hourlyRates
    );
  }
}
