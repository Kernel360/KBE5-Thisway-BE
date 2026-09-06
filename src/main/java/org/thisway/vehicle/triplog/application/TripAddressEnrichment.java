package org.thisway.vehicle.triplog.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.thisway.vehicle.triplog.domain.ReverseGeocodingConverter;

/** Coordinates are durable in TripLog; a failed lookup leaves the address NULL for explicit retry. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripAddressEnrichment {
    private final JdbcTemplate jdbc;
    private final ReverseGeocodingConverter converter;
    private final PlatformTransactionManager transactions;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void afterCommit(TripAddressRequested event) {
        enrich(event.tripId(), event.off());
    }

    /** Internal retry entry point, not a public HTTP endpoint or automatic job. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean enrich(long tripId, boolean off) {
        String side = off ? "off" : "on"; // SQL identifiers are fixed, never request strings.
        try {
            var points = jdbc.query("SELECT " + side + "_latitude," + side + "_longitude FROM trip_log "
                            + "WHERE id=? AND " + side + "_addr IS NULL AND " + side + "_latitude IS NOT NULL "
                            + "AND " + side + "_longitude IS NOT NULL",
                    (rs, row) -> new double[]{rs.getDouble(1), rs.getDouble(2)}, tripId);
            if (points.isEmpty()) return false;
            double[] point = points.getFirst();
            // No DB transaction or row lock is held during external I/O.
            var address = converter.convertToAddress(point[0], point[1]);
            var write = new TransactionTemplate(transactions);
            write.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
            return Boolean.TRUE.equals(write.execute(status -> jdbc.update("UPDATE trip_log SET " + side
                            + "_addr=?," + side + "_addr_detail=? WHERE id=? AND " + side + "_addr IS NULL "
                            + "AND " + side + "_latitude=? AND " + side + "_longitude=?",
                    address.addr(), address.addrDetail(), tripId, point[0], point[1]) == 1));
        } catch (RuntimeException failure) {
            // Do not log exception text: HTTP exceptions can contain URLs and coordinates.
            log.warn("운행 주소 보정 보류: tripId={}, side={}, failureType={}",
                    tripId, side, failure.getClass().getSimpleName());
            return false;
        }
    }
}
