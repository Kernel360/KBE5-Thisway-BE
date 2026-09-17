package org.thisway.company.statistics.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "statistics_revision", uniqueConstraints = @UniqueConstraint(
        name = "uk_statistics_revision", columnNames = {"company_id", "target_date", "revision"}))
public class StatisticsRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private long companyId;
    @Column(nullable = false) private LocalDate targetDate;
    @Column(nullable = false) private long revision;
    @Column(nullable = false, length = 40) private String reason;
    @Column(nullable = false) private LocalDateTime recordedAt;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String snapshotJson;

    protected StatisticsRevision() { }

    public StatisticsRevision(long companyId, LocalDate targetDate, long revision, String reason,
                              LocalDateTime recordedAt, String snapshotJson) {
        this.companyId = companyId;
        this.targetDate = targetDate;
        this.revision = revision;
        this.reason = reason;
        this.recordedAt = recordedAt;
        this.snapshotJson = snapshotJson;
    }
}
