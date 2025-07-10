package org.thisway.company.statistics.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thisway.company.statistics.domain.Statistics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StatisticsRepository extends JpaRepository<Statistics, Long> {

    @Query("SELECT s FROM Statistics s WHERE s.company.id = :companyId AND s.date >= :startOfDay AND s.date < :startOfNextDay")
    Optional<Statistics> getStatisticByCompanyIdAndDate(@Param("companyId") Long companyId, @Param("startOfDay") LocalDateTime startOfDay, @Param("startOfNextDay") LocalDateTime startOfNextDay);

    @Query("SELECT s FROM Statistics s WHERE s.company.id = :companyId AND DATE(s.date) >= :startDate AND DATE(s.date) <= :endDate ORDER BY s.date")
    List<Statistics> findByCompanyIdAndDateRange(@Param("companyId") Long companyId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
