package org.thisway.triplog.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.thisway.triplog.dto.response.TripLocationStats;
import org.thisway.triplog.entity.TripLog;

import java.time.LocalDateTime;
import java.util.List;

public interface TripLogRepository extends JpaRepository<TripLog, Long> {

    @Query("SELECT new org.thisway.triplog.dto.response.TripLocationStats(t.onAddr, COUNT(t)) " +
            "FROM TripLog t " +
            "WHERE t.vehicle.company.id = :companyId " +
            "AND t.startTime >= :from AND t.startTime <= :to " +
            "GROUP BY t.onAddr"
    )
    List<TripLocationStats> countGroupedByOnAddr(
            @Param("companyId")Long companyId,
            @Param("from")LocalDateTime from,
            @Param("to")LocalDateTime to
    );
}
