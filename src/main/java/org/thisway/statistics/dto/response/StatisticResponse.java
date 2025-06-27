package org.thisway.statistics.dto.response;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import org.thisway.statistics.entity.Statistics;
import org.thisway.statistics.constant.StatisticConstants;

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
    Integer peakHourRate = getSafeHourlyRate(statistics.getPeakHour(), hours);
    Integer lowHourRate = getSafeHourlyRate(statistics.getLowHour(), hours);

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

    Integer peakHourRate = getSafeHourlyRate(peakHour, hourlyRates);
    Integer lowHourRate = getSafeHourlyRate(lowHour, hourlyRates);

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

  private static Integer getSafeHourlyRate(Integer hour, List<Integer> hourlyRates) {
    if (hour == null || hour < 0 || hour >= hourlyRates.size()) {
      return StatisticConstants.DEFAULT_HOURLY_RATE;
    }
    return hourlyRates.get(hour);
  }
}
