package org.thisway.company.statistics.domain;

import java.time.LocalDate;

public interface StatisticsFleetSnapshots {
    boolean exists(long companyId, LocalDate date);
    void captureCurrent(long companyId, LocalDate date);
    long size(long companyId, LocalDate date);
    long countKnown(long companyId, LocalDate from, LocalDate through, int formulaVersion);
}
