package org.thisway.statistics.constant;

/**
 * 통계 관련 상수 클래스
 * 매직 넘버와 반복되는 값들을 중앙 집중화하여 관리
 */
public final class StatisticConstants {
    
    // 시간 관련 상수
    public static final int HOURS_IN_DAY = 24;
    public static final int SECONDS_IN_HOUR = 3600;

    // 계산 관련 상수
    public static final int PERCENTAGE_MULTIPLIER = 100;
    public static final double DEFAULT_OPERATION_RATE = 0.0;
    
    // 시간대별 상수
    public static final int HOUR_00 = 0;
    public static final int HOUR_01 = 1;
    public static final int HOUR_02 = 2;
    public static final int HOUR_03 = 3;
    public static final int HOUR_04 = 4;
    public static final int HOUR_05 = 5;
    public static final int HOUR_06 = 6;
    public static final int HOUR_07 = 7;
    public static final int HOUR_08 = 8;
    public static final int HOUR_09 = 9;
    public static final int HOUR_10 = 10;
    public static final int HOUR_11 = 11;
    public static final int HOUR_12 = 12;
    public static final int HOUR_13 = 13;
    public static final int HOUR_14 = 14;
    public static final int HOUR_15 = 15;
    public static final int HOUR_16 = 16;
    public static final int HOUR_17 = 17;
    public static final int HOUR_18 = 18;
    public static final int HOUR_19 = 19;
    public static final int HOUR_20 = 20;
    public static final int HOUR_21 = 21;
    public static final int HOUR_22 = 22;
    public static final int HOUR_23 = 23;
    
    // 기본값 상수
    public static final int DEFAULT_HOURLY_RATE = 0;
    public static final int DEFAULT_PEAK_HOUR = 0;
    public static final int DEFAULT_LOW_HOUR = 0;
    
    // 날짜/시간 포맷 상수
    public static final String DATE_RANGE_SEPARATOR = " ~ ";
    
    // 로그 메시지 상수
    public static final String LOG_CALCULATION_START = "=== 시간대별 가동률 계산 시작 ===";
    public static final String LOG_CALCULATION_COMPLETE = "=== 시간대별 가동률 계산 완료 ===";
    public static final String LOG_NO_VEHICLES = "차량이 없어서 모든 시간대 가동률을 0으로 설정";
    public static final String LOG_SAVE_STATISTICS_CALL = "=== saveStatistics 호출 ===";
    public static final String LOG_STATISTICS_UPDATE_COMPLETE = "기존 통계 업데이트 완료: 회사 ID {}, 날짜 {}";
    public static final String LOG_STATISTICS_SAVE_COMPLETE = "신규 통계 저장 완료: 회사 ID {}, 날짜 {}";
    
    // API 호출 로그 메시지
    public static final String LOG_API_DATE_RANGE = "=== 날짜 범위 통계 조회 API 호출 ===";
    public static final String LOG_API_SAVE = "=== 통계 저장 API 호출 ===";
    
    // 유틸리티 클래스 방지
    private StatisticConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
} 
