package org.thisway.company.statistics.domain;

/** Append a calculated aggregate within the caller's transaction. */
public interface StatisticsRevisionRecorder {
    void append(Statistics value, String reason);
}
