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
    
    // 기본값 상수
    public static final int DEFAULT_HOURLY_RATE = 0;
    public static final int DEFAULT_PEAK_HOUR = 0;
    public static final int DEFAULT_LOW_HOUR = 0;
    
    // 날짜/시간 포맷 상수
    public static final String DATE_RANGE_SEPARATOR = " ~ ";
} 
