package org.thisway.company.statistics.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "statistics_fleet_member", uniqueConstraints = @UniqueConstraint(
        name = "uk_statistics_fleet_member", columnNames = {"company_id", "target_date", "vehicle_id"}))
public class StatisticsFleetMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private long companyId;
    @Column(nullable = false) private LocalDate targetDate;
    @Column(nullable = false) private long vehicleId;
    protected StatisticsFleetMember() { }
}
