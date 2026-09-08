package org.thisway.company.statistics.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Mapping also keeps the isolated JPA test schema aligned with the Flyway-owned queue. */
@Entity
@Table(name = "statistics_correction_request", uniqueConstraints =
        @UniqueConstraint(name = "uk_statistics_correction_day", columnNames = {"company_id", "target_date"}))
public class StatisticsCorrectionRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private long companyId;
    @Column(nullable = false) private LocalDate targetDate;
    @Column(nullable = false) private long requestedGeneration;
    @Column(nullable = false) private long completedGeneration;
    @Column(nullable = false, length = 32) private String reason;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private LocalDateTime nextAttemptAt;
    @Column(nullable = false) private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    @Column(length = 120) private String lastError;
    protected StatisticsCorrectionRequest() { }
}
