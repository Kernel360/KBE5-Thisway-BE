package org.thisway.triplog.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.thisway.triplog.dto.TripLocationRaw;
import org.thisway.triplog.entity.TripLog;

import java.time.LocalDateTime;
import java.util.List;

public interface TripLogRepository extends JpaRepository<TripLog, Long> {

    @Query("""
        SELECT new org.thisway.triplog.dto.TripLocationRaw(t.onAddr, COUNT(t))
        FROM TripLog t
        WHERE t.vehicle.company.id = :companyId
            AND t.startTime >= :from
            AND t.startTime <= :to
        GROUP BY t.onAddr
        ORDER BY COUNT(t) desc
    """)
    List<TripLocationRaw> countGroupedByOnAddr(
            @Param("companyId")Long companyId,
            @Param("from")LocalDateTime from,
            @Param("to")LocalDateTime to
    );

    @Query("""
        SELECT t FROM TripLog t
        JOIN t.vehicle v
        WHERE v.company.id = :companyId AND t.active = true
        ORDER BY t.startTime DESC
    """)
    Page<TripLog> findAllByCompanyAndActiveTrueOrderByStartTimeDesc(@Param("companyId") Long companyId, Pageable pageable);

    List<TripLog> findTop6ByVehicleIdOrderByStartTimeDesc(Long vehicleId);

    TripLog findByVehicleIdAndStartTime(Long vehicleId, LocalDateTime startTime);


    // 특정 회사의 날짜 범위에 대한 시동 횟수
    @Query("SELECT COUNT(t) FROM TripLog t " +
           "WHERE t.vehicle.company.id = :companyId " +
           "AND t.startTime >= :startDate AND t.startTime <= :endDate")
    Long countPowerOnByCompanyAndDateRange(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // 특정 회사의 날짜 범위에 대한 총 운행시간을 분 단위로 계산
    @Query("SELECT t FROM TripLog t " +
           "WHERE t.vehicle.company.id = :companyId AND t.active = true " +
           "AND t.startTime >= :startDate AND t.startTime <= :endDate")
    List<TripLog> findTripLogsByCompanyAndDateRange(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("""
        SELECT t.startTime FROM TripLog t
        WHERE t.vehicle.id = :vehicleId AND t.active = false
        ORDER BY t.startTime DESC
        LIMIT 1
    """)
    LocalDateTime findTop1StartTimeByVehicleId(@Param("vehicleId") Long vehicleId);
}
