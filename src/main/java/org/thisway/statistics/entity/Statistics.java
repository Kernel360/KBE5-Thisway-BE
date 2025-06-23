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
    this.hour00 = hour00 != null ? hour00 : 0;
    this.hour01 = hour01 != null ? hour01 : 0;
    this.hour02 = hour02 != null ? hour02 : 0;
    this.hour03 = hour03 != null ? hour03 : 0;
    this.hour04 = hour04 != null ? hour04 : 0;
    this.hour05 = hour05 != null ? hour05 : 0;
    this.hour06 = hour06 != null ? hour06 : 0;
    this.hour07 = hour07 != null ? hour07 : 0;
    this.hour08 = hour08 != null ? hour08 : 0;
    this.hour09 = hour09 != null ? hour09 : 0;
    this.hour10 = hour10 != null ? hour10 : 0;
    this.hour11 = hour11 != null ? hour11 : 0;
    this.hour12 = hour12 != null ? hour12 : 0;
    this.hour13 = hour13 != null ? hour13 : 0;
    this.hour14 = hour14 != null ? hour14 : 0;
    this.hour15 = hour15 != null ? hour15 : 0;
    this.hour16 = hour16 != null ? hour16 : 0;
    this.hour17 = hour17 != null ? hour17 : 0;
    this.hour18 = hour18 != null ? hour18 : 0;
    this.hour19 = hour19 != null ? hour19 : 0;
    this.hour20 = hour20 != null ? hour20 : 0;
    this.hour21 = hour21 != null ? hour21 : 0;
    this.hour22 = hour22 != null ? hour22 : 0;
    this.hour23 = hour23 != null ? hour23 : 0;
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

  // 시간대별 가동률 업데이트 메서드 (필요시 사용)
  public void updateHourlyRates(Integer[] hourlyRates) {
    if (hourlyRates != null && hourlyRates.length == 24) {
      this.hour00 = hourlyRates[0] != null ? hourlyRates[0] : 0;
      this.hour01 = hourlyRates[1] != null ? hourlyRates[1] : 0;
      this.hour02 = hourlyRates[2] != null ? hourlyRates[2] : 0;
      this.hour03 = hourlyRates[3] != null ? hourlyRates[3] : 0;
      this.hour04 = hourlyRates[4] != null ? hourlyRates[4] : 0;
      this.hour05 = hourlyRates[5] != null ? hourlyRates[5] : 0;
      this.hour06 = hourlyRates[6] != null ? hourlyRates[6] : 0;
      this.hour07 = hourlyRates[7] != null ? hourlyRates[7] : 0;
      this.hour08 = hourlyRates[8] != null ? hourlyRates[8] : 0;
      this.hour09 = hourlyRates[9] != null ? hourlyRates[9] : 0;
      this.hour10 = hourlyRates[10] != null ? hourlyRates[10] : 0;
      this.hour11 = hourlyRates[11] != null ? hourlyRates[11] : 0;
      this.hour12 = hourlyRates[12] != null ? hourlyRates[12] : 0;
      this.hour13 = hourlyRates[13] != null ? hourlyRates[13] : 0;
      this.hour14 = hourlyRates[14] != null ? hourlyRates[14] : 0;
      this.hour15 = hourlyRates[15] != null ? hourlyRates[15] : 0;
      this.hour16 = hourlyRates[16] != null ? hourlyRates[16] : 0;
      this.hour17 = hourlyRates[17] != null ? hourlyRates[17] : 0;
      this.hour18 = hourlyRates[18] != null ? hourlyRates[18] : 0;
      this.hour19 = hourlyRates[19] != null ? hourlyRates[19] : 0;
      this.hour20 = hourlyRates[20] != null ? hourlyRates[20] : 0;
      this.hour21 = hourlyRates[21] != null ? hourlyRates[21] : 0;
      this.hour22 = hourlyRates[22] != null ? hourlyRates[22] : 0;
      this.hour23 = hourlyRates[23] != null ? hourlyRates[23] : 0;
    }
  }
}
