package org.thisway.statistics.dto.response;

import java.time.format.DateTimeFormatter;
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

    List<Integer> hours = List.of(
        statistics.getHour00(), statistics.getHour01(), statistics.getHour02(),
        statistics.getHour03(), statistics.getHour04(), statistics.getHour05(),
        statistics.getHour06(), statistics.getHour07(), statistics.getHour08(),
        statistics.getHour09(), statistics.getHour10(), statistics.getHour11(),
        statistics.getHour12(), statistics.getHour13(), statistics.getHour14(),
        statistics.getHour15(), statistics.getHour16(), statistics.getHour17(),
        statistics.getHour18(), statistics.getHour19(), statistics.getHour20(),
        statistics.getHour21(), statistics.getHour22(), statistics.getHour23()
    );

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
