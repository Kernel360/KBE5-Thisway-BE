package org.thisway.company.statistics.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "statistics_fleet_snapshot", uniqueConstraints = @UniqueConstraint(
        name = "uk_statistics_fleet_snapshot", columnNames = {"company_id", "target_date"}))
public class StatisticsFleetSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private long companyId;
    @Column(nullable = false) private LocalDate targetDate;
    @Column(nullable = false) private LocalDateTime capturedAt;
    protected StatisticsFleetSnapshot() { }
}
