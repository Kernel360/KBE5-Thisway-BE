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
  private Double hour00;

  @Column(nullable = false)
  private Double hour01;

  @Column(nullable = false)
  private Double hour02;

  @Column(nullable = false)
  private Double hour03;

  @Column(nullable = false)
  private Double hour04;

  @Column(nullable = false)
  private Double hour05;

  @Column(nullable = false)
  private Double hour06;

  @Column(nullable = false)
  private Double hour07;

  @Column(nullable = false)
  private Double hour08;

  @Column(nullable = false)
  private Double hour09;

  @Column(nullable = false)
  private Double hour10;

  @Column(nullable = false)
  private Double hour11;

  @Column(nullable = false)
  private Double hour12;

  @Column(nullable = false)
  private Double hour13;

  @Column(nullable = false)
  private Double hour14;

  @Column(nullable = false)
  private Double hour15;

  @Column(nullable = false)
  private Double hour16;

  @Column(nullable = false)
  private Double hour17;

  @Column(nullable = false)
  private Double hour18;

  @Column(nullable = false)
  private Double hour19;

  @Column(nullable = false)
  private Double hour20;

  @Column(nullable = false)
  private Double hour21;

  @Column(nullable = false)
  private Double hour22;

  @Column(nullable = false)
  private Double hour23;

  @Builder
  public Statistics(Company company, LocalDateTime date, Integer powerOnCount,
      Double averageDailyPowerCount, Integer totalDrivingTime,
      Integer peakHour, Integer lowHour, Double averageOperationRate,
      Double hour00, Double hour01, Double hour02, Double hour03,
      Double hour04, Double hour05, Double hour06, Double hour07,
      Double hour08, Double hour09, Double hour10, Double hour11,
      Double hour12, Double hour13, Double hour14, Double hour15,
      Double hour16, Double hour17, Double hour18, Double hour19,
      Double hour20, Double hour21, Double hour22, Double hour23) {
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
  public void updateHourlyRates(double[] hourlyRates) {
    if (hourlyRates != null && hourlyRates.length == StatisticConstants.HOURS_IN_DAY) {
      this.hour00 = hourlyRates[0];
      this.hour01 = hourlyRates[1];
      this.hour02 = hourlyRates[2];
      this.hour03 = hourlyRates[3];
      this.hour04 = hourlyRates[4];
      this.hour05 = hourlyRates[5];
      this.hour06 = hourlyRates[6];
      this.hour07 = hourlyRates[7];
      this.hour08 = hourlyRates[8];
      this.hour09 = hourlyRates[9];
      this.hour10 = hourlyRates[10];
      this.hour11 = hourlyRates[11];
      this.hour12 = hourlyRates[12];
      this.hour13 = hourlyRates[13];
      this.hour14 = hourlyRates[14];
      this.hour15 = hourlyRates[15];
      this.hour16 = hourlyRates[16];
      this.hour17 = hourlyRates[17];
      this.hour18 = hourlyRates[18];
      this.hour19 = hourlyRates[19];
      this.hour20 = hourlyRates[20];
      this.hour21 = hourlyRates[21];
      this.hour22 = hourlyRates[22];
      this.hour23 = hourlyRates[23];
    }
  }

  // 안전한 값 반환
  private Double getSafeValue(Double value) {
    return value != null ? value : StatisticConstants.DEFAULT_HOURLY_RATE;
  }

  // 특정 시간대 가동률 조회
  public Double getHourlyRate(int hour) {
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
  public Double[] getHourlyRatesArray() {
    return new Double[]{
      hour00, hour01, hour02, hour03, hour04, hour05, hour06, hour07,
      hour08, hour09, hour10, hour11, hour12, hour13, hour14, hour15,
      hour16, hour17, hour18, hour19, hour20, hour21, hour22, hour23
    };
  }
}
