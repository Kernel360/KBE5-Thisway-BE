package org.thisway.triplog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.triplog.entity.TripLog;

public interface TripLogRepository extends JpaRepository<TripLog, Long> {

}
