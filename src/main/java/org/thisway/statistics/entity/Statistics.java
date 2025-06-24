package org.thisway.statistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.common.BaseEntity;
import org.thisway.company.entity.Company;
import org.thisway.statistics.constant.StatisticConstants;

@Entity
@Getter
@NoArgsConstructor
public class Statistics extends BaseEntity {

  private static final int HOURS_IN_DAY = 24;
  private static final int DEFAULT_HOURLY_RATE = 0;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @Column(nullable = false)
  private LocalDateTime date;

  @Column(nullable = false)
  private Integer powerOnCount;

  @Column
  private Double averageDailyPowerCount;

  @Column
  private Integer totalDrivingTime;

  @Column
  private Integer peakHour;

  @Column
  private Integer lowHour;

  @Column
  private Double averageOperationRate; // 평균 가동률

  // 시간대별 가동률
  @Column(nullable = false)
  private Integer hour00;

  @Column(nullable = false)
  private Integer hour01;

  @Column(nullable = false)
  private Integer hour02;

  @Column(nullable = false)
  private Integer hour03;

  @Column(nullable = false)
  private Integer hour04;

  @Column(nullable = false)
  private Integer hour05;

  @Column(nullable = false)
  private Integer hour06;

  @Column(nullable = false)
  private Integer hour07;

  @Column(nullable = false)
  private Integer hour08;

  @Column(nullable = false)
  private Integer hour09;

  @Column(nullable = false)
  private Integer hour10;

  @Column(nullable = false)
  private Integer hour11;

  @Column(nullable = false)
  private Integer hour12;

  @Column(nullable = false)
  private Integer hour13;

  @Column(nullable = false)
  private Integer hour14;

  @Column(nullable = false)
  private Integer hour15;

  @Column(nullable = false)
  private Integer hour16;

  @Column(nullable = false)
  private Integer hour17;

  @Column(nullable = false)
  private Integer hour18;

  @Column(nullable = false)
  private Integer hour19;

  @Column(nullable = false)
  private Integer hour20;

  @Column(nullable = false)
  private Integer hour21;

  @Column(nullable = false)
  private Integer hour22;

  @Column(nullable = false)
  private Integer hour23;

  @Builder
  public Statistics(Company company, LocalDateTime date, Integer powerOnCount,
      Double averageDailyPowerCount, Integer totalDrivingTime,
      Integer peakHour, Integer lowHour, Double averageOperationRate,
      Integer hour00, Integer hour01, Integer hour02, Integer hour03,
      Integer hour04, Integer hour05, Integer hour06, Integer hour07,
      Integer hour08, Integer hour09, Integer hour10, Integer hour11,
      Integer hour12, Integer hour13, Integer hour14, Integer hour15,
      Integer hour16, Integer hour17, Integer hour18, Integer hour19,
      Integer hour20, Integer hour21, Integer hour22, Integer hour23) {
    this.company = company;
    this.date = date;
    this.powerOnCount = powerOnCount;
    this.averageDailyPowerCount = averageDailyPowerCount;
    this.totalDrivingTime = totalDrivingTime;
    this.peakHour = peakHour;
    this.lowHour = lowHour;
    this.averageOperationRate = averageOperationRate;
    
    // 시간대별 가동률 설정
    this.hour00 = hour00 != null ? hour00 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour01 = hour01 != null ? hour01 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour02 = hour02 != null ? hour02 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour03 = hour03 != null ? hour03 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour04 = hour04 != null ? hour04 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour05 = hour05 != null ? hour05 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour06 = hour06 != null ? hour06 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour07 = hour07 != null ? hour07 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour08 = hour08 != null ? hour08 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour09 = hour09 != null ? hour09 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour10 = hour10 != null ? hour10 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour11 = hour11 != null ? hour11 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour12 = hour12 != null ? hour12 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour13 = hour13 != null ? hour13 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour14 = hour14 != null ? hour14 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour15 = hour15 != null ? hour15 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour16 = hour16 != null ? hour16 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour17 = hour17 != null ? hour17 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour18 = hour18 != null ? hour18 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour19 = hour19 != null ? hour19 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour20 = hour20 != null ? hour20 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour21 = hour21 != null ? hour21 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour22 = hour22 != null ? hour22 : StatisticConstants.DEFAULT_HOURLY_RATE;
    this.hour23 = hour23 != null ? hour23 : StatisticConstants.DEFAULT_HOURLY_RATE;
  }

  // 통계 데이터 업데이트 메서드
  public void updateStatistics(Integer powerOnCount, Double averageDailyPowerCount, Integer totalDrivingTime,
                             Integer peakHour, Integer lowHour, Double averageOperationRate) {
    this.powerOnCount = powerOnCount;
    this.averageDailyPowerCount = averageDailyPowerCount;
    this.totalDrivingTime = totalDrivingTime;
    this.peakHour = peakHour;
    this.lowHour = lowHour;
    this.averageOperationRate = averageOperationRate;
  }

  // 시간대별 가동률 업데이트 메서드
  public void updateHourlyRates(Integer[] hourlyRates) {
    if (hourlyRates != null && hourlyRates.length == StatisticConstants.HOURS_IN_DAY) {
      this.hour00 = getSafeValue(hourlyRates[0]);
      this.hour01 = getSafeValue(hourlyRates[1]);
      this.hour02 = getSafeValue(hourlyRates[2]);
      this.hour03 = getSafeValue(hourlyRates[3]);
      this.hour04 = getSafeValue(hourlyRates[4]);
      this.hour05 = getSafeValue(hourlyRates[5]);
      this.hour06 = getSafeValue(hourlyRates[6]);
      this.hour07 = getSafeValue(hourlyRates[7]);
      this.hour08 = getSafeValue(hourlyRates[8]);
      this.hour09 = getSafeValue(hourlyRates[9]);
      this.hour10 = getSafeValue(hourlyRates[10]);
      this.hour11 = getSafeValue(hourlyRates[11]);
      this.hour12 = getSafeValue(hourlyRates[12]);
      this.hour13 = getSafeValue(hourlyRates[13]);
      this.hour14 = getSafeValue(hourlyRates[14]);
      this.hour15 = getSafeValue(hourlyRates[15]);
      this.hour16 = getSafeValue(hourlyRates[16]);
      this.hour17 = getSafeValue(hourlyRates[17]);
      this.hour18 = getSafeValue(hourlyRates[18]);
      this.hour19 = getSafeValue(hourlyRates[19]);
      this.hour20 = getSafeValue(hourlyRates[20]);
      this.hour21 = getSafeValue(hourlyRates[21]);
      this.hour22 = getSafeValue(hourlyRates[22]);
      this.hour23 = getSafeValue(hourlyRates[23]);
    }
  }

  // 안전한 값 반환
  private Integer getSafeValue(Integer value) {
    return value != null ? value : StatisticConstants.DEFAULT_HOURLY_RATE;
  }

  // 특정 시간대 가동률 조회
  public Integer getHourlyRate(int hour) {
    if (hour < 0 || hour >= StatisticConstants.HOURS_IN_DAY) {
      return StatisticConstants.DEFAULT_HOURLY_RATE;
    }
    
    return switch (hour) {
      case 0 -> hour00;
      case 1 -> hour01;
      case 2 -> hour02;
      case 3 -> hour03;
      case 4 -> hour04;
      case 5 -> hour05;
      case 6 -> hour06;
      case 7 -> hour07;
      case 8 -> hour08;
      case 9 -> hour09;
      case 10 -> hour10;
      case 11 -> hour11;
      case 12 -> hour12;
      case 13 -> hour13;
      case 14 -> hour14;
      case 15 -> hour15;
      case 16 -> hour16;
      case 17 -> hour17;
      case 18 -> hour18;
      case 19 -> hour19;
      case 20 -> hour20;
      case 21 -> hour21;
      case 22 -> hour22;
      case 23 -> hour23;
      default -> StatisticConstants.DEFAULT_HOURLY_RATE;
    };
  }

  /**
   * 시간대별 가동률을 배열로 반환
   */
  public Integer[] getHourlyRatesArray() {
    return new Integer[]{
      hour00, hour01, hour02, hour03, hour04, hour05, hour06, hour07,
      hour08, hour09, hour10, hour11, hour12, hour13, hour14, hour15,
      hour16, hour17, hour18, hour19, hour20, hour21, hour22, hour23
    };
  }
}
