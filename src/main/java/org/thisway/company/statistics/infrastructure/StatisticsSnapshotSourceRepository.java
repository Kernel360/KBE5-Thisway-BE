package org.thisway.company.statistics.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.thisway.company.statistics.domain.*;
import org.thisway.vehicle.triplog.domain.TripLocationRaw;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class StatisticsSnapshotSourceRepository implements StatisticsFleetSnapshots, StatisticsSourceReader {
    private final JdbcTemplate jdbc;

    @Override
    public boolean exists(long companyId, LocalDate date) {
        return !jdbc.query("SELECT id FROM statistics_fleet_snapshot WHERE company_id=? AND target_date=? FOR UPDATE",
                (rs, row) -> rs.getLong(1), companyId, date).isEmpty();
    }

    @Override
    public void captureCurrent(long companyId, LocalDate date) {
        // The caller owns the company lock. Avoid vehicle locking reads: ingestion locks vehicle before company.
        var ids = jdbc.query("SELECT id FROM vehicle WHERE company_id=? AND active=TRUE ORDER BY id",
                (rs, row) -> rs.getLong(1), companyId);
        jdbc.update("INSERT INTO statistics_fleet_snapshot(company_id,target_date,captured_at) VALUES(?,?,CURRENT_TIMESTAMP(6))",
                companyId, date);
        for (long id : ids) jdbc.update("INSERT INTO statistics_fleet_member(company_id,target_date,vehicle_id) VALUES(?,?,?)",
                companyId, date, id);
    }

    @Override
    public long size(long companyId, LocalDate date) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM statistics_fleet_member WHERE company_id=? AND target_date=?",
                Long.class, companyId, date);
    }

    @Override
    public long countKnown(long companyId, LocalDate from, LocalDate through, int formulaVersion) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM statistics_fleet_snapshot f JOIN statistics s
                ON s.company_id=f.company_id AND DATE(s.date)=f.target_date
                WHERE f.company_id=? AND f.target_date>=? AND f.target_date<=? AND s.formula_version=?
                """, Long.class, companyId, from, through, formulaVersion);
    }

    @Override
    public List<CompletedTripTime.Interval> completed(long companyId, LocalDateTime start, LocalDateTime end) {
        return jdbc.query("""
                SELECT t.vehicle_id,t.start_time,t.end_time FROM trip_log t
                JOIN statistics_fleet_member f ON f.vehicle_id=t.vehicle_id AND f.company_id=? AND f.target_date=?
                WHERE t.active=TRUE AND t.end_time>t.start_time AND t.start_time<? AND t.end_time>?
                """, (rs, row) -> new CompletedTripTime.Interval(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(),
                rs.getTimestamp(3).toLocalDateTime()), companyId, start.toLocalDate(), end, start);
    }

    @Override
    public long starts(long companyId, LocalDateTime start, LocalDateTime end) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT DISTINCT t.vehicle_id,t.start_time FROM trip_log t
                    JOIN statistics_fleet_member f ON f.vehicle_id=t.vehicle_id AND f.company_id=? AND f.target_date=?
                    WHERE t.start_time>=? AND t.start_time<?
                ) starts
                """, Long.class, companyId, start.toLocalDate(), start, end);
    }

    @Override
    public long gps(long companyId, LocalDateTime start, LocalDateTime end) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM gps_log g JOIN statistics_fleet_member f
                ON f.vehicle_id=g.vehicle_id AND f.company_id=? AND f.target_date=?
                WHERE g.occurred_time>=? AND g.occurred_time<?
                """, Long.class, companyId, start.toLocalDate(), start, end);
    }

    @Override
    public long unclosed(long companyId, LocalDateTime start, LocalDateTime end) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM trip_log t JOIN statistics_fleet_member f
                ON f.vehicle_id=t.vehicle_id AND f.company_id=? AND f.target_date=?
                WHERE t.end_time IS NULL AND t.start_time<?
                """, Long.class, companyId, start.toLocalDate(), end);
    }

    @Override
    public List<TripLocationRaw> locations(long companyId, LocalDateTime start, LocalDateTime end) {
        return jdbc.query("""
                SELECT t.on_addr,COUNT(*) FROM trip_log t JOIN statistics_fleet_member f
                ON f.vehicle_id=t.vehicle_id AND f.company_id=? AND f.target_date=DATE(t.start_time)
                WHERE t.start_time>=? AND t.start_time<=?
                GROUP BY t.on_addr ORDER BY COUNT(*) DESC
                """, (rs, row) -> new TripLocationRaw(rs.getString(1), rs.getLong(2)), companyId, start, end);
    }
}
