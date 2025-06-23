package org.thisway.statistics.dto.response;

import java.time.format.DateTimeFormatter;
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
    Integer hour00,
    Integer hour01,
    Integer hour02,
    Integer hour03,
    Integer hour04,
    Integer hour05,
    Integer hour06,
    Integer hour07,
    Integer hour08,
    Integer hour09,
    Integer hour10,
    Integer hour11,
    Integer hour12,
    Integer hour13,
    Integer hour14,
    Integer hour15,
    Integer hour16,
    Integer hour17,
    Integer hour18,
    Integer hour19,
    Integer hour20,
    Integer hour21,
    Integer hour22,
    Integer hour23
) {
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  public static StatisticResponse from(Statistics statistics){
    return new StatisticResponse(
        statistics.getCompany().getId(),
        statistics.getDate().format(FORMATTER),
        statistics.getPowerOnCount(),
        statistics.getAverageDailyPowerCount(),
        statistics.getTotalDrivingTime(),
        statistics.getPeakHour(),
        statistics.getLowHour(),
        statistics.getAverageOperationRate(),
        statistics.getHour00(),
        statistics.getHour01(),
        statistics.getHour02(),
        statistics.getHour03(),
        statistics.getHour04(),
        statistics.getHour05(),
        statistics.getHour06(),
        statistics.getHour07(),
        statistics.getHour08(),
        statistics.getHour09(),
        statistics.getHour10(),
        statistics.getHour11(),
        statistics.getHour12(),
        statistics.getHour13(),
        statistics.getHour14(),
        statistics.getHour15(),
        statistics.getHour16(),
        statistics.getHour17(),
        statistics.getHour18(),
        statistics.getHour19(),
        statistics.getHour20(),
        statistics.getHour21(),
        statistics.getHour22(),
        statistics.getHour23()
    );

  }
}
