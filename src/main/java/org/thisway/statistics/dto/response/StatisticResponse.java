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
    Integer peakHourRate,
    Integer lowHour,
    Integer lowHourRate,
    Double averageOperationRate,
    List<Integer> hours
) {
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  public static StatisticResponse from(Statistics statistics){
    // Statistics 엔티티의 getHourlyRatesArray() 메서드 활용
    List<Integer> hours = Arrays.asList(statistics.getHourlyRatesArray());

    // peakHour와 lowHour의 실제 퍼센트 값 계산
    Integer peakHourRate = statistics.getHourlyRate(statistics.getPeakHour());
    Integer lowHourRate = statistics.getHourlyRate(statistics.getLowHour());

    return new StatisticResponse(
        statistics.getCompany().getId(),
        statistics.getDate().format(FORMATTER),
        statistics.getPowerOnCount(),
        statistics.getAverageDailyPowerCount(),
        statistics.getTotalDrivingTime(),
        statistics.getPeakHour(),
        peakHourRate,
        statistics.getLowHour(),
        lowHourRate,
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

    Integer peakHourRate = (peakHour >= 0 && peakHour < hourlyRates.size()) ?
        hourlyRates.get(peakHour) : 0;
    Integer lowHourRate = (lowHour >= 0 && lowHour < hourlyRates.size()) ? 
        hourlyRates.get(lowHour) : 0;

    return new StatisticResponse(
        companyId,
        dateRange,
        powerOnCount,
        averageDailyPowerCount,
        totalDrivingTime,
        peakHour,
        peakHourRate,
        lowHour,
        lowHourRate,
        averageOperationRate,
        hourlyRates
    );
  }
}
