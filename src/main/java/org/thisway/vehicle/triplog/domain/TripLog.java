package org.thisway.vehicle.triplog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.support.common.BaseEntity;
import org.thisway.vehicle.domain.Vehicle;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_trip_observed_start", columnNames = {"vehicle_id", "identity_start_time"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripLog extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column
    private LocalDateTime endTime;

    @Column(nullable = false)
    private Integer totalTripMeter;

    private LocalDateTime identityStartTime;
    private Integer startOdometer;
    private Integer endOdometer;
    private Integer distanceMeters;

    @Column
    private Double onLatitude;

    @Column
    private Double onLongitude;

    @Column
    private String onAddr;

    @Column
    private String onAddrDetail;

    @Column
    private Double offLatitude;

    @Column
    private Double offLongitude;

    @Column
    private String offAddr;

    @Column
    private String offAddrDetail;

    @Builder
    public TripLog(
            Vehicle vehicle,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer totalTripMeter,
            Double onLatitude,
            Double onLongitude,
            String onAddress,
            String onAddrDetail,
            Double offLatitude,
            Double offLongitude,
            String offAddress,
            String offAddrDetail,
            Boolean active
    ) {
        this.vehicle = vehicle;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalTripMeter = totalTripMeter;
        this.onLatitude = onLatitude;
        this.onLongitude = onLongitude;
        this.onAddr = onAddress;
        this.onAddrDetail = onAddrDetail;
        this.offLatitude = offLatitude;
        this.offLongitude = offLongitude;
        this.offAddr = offAddress;
        this.offAddrDetail = offAddrDetail;
        this.updateActive(active);
    }

    public static TripLog observed(Vehicle vehicle, LocalDateTime startTime) {
        TripLog trip = TripLog.builder().vehicle(vehicle).startTime(startTime)
                .totalTripMeter(0).active(false).build();
        trip.identityStartTime = Objects.requireNonNull(startTime);
        return trip;
    }

    /** Returns false for a duplicate; never clears existing addresses or reopens a completed trip. */
    public boolean observe(TripLogSaveInput input) {
        input.validate();
        if (identityStartTime == null || !startTime.equals(input.onTime())) {
            throw new TripObservationConflictException();
        }
        if (input.offTime() == null) {
            if (startOdometer != null) {
                if (!Objects.equals(startOdometer, input.odometer())
                        || !Objects.equals(onLatitude, input.latitude())
                        || !Objects.equals(onLongitude, input.longitude())) throw new TripObservationConflictException();
                return false;
            }
            startOdometer = input.odometer();
            onLatitude = input.latitude();
            onLongitude = input.longitude();
        } else {
            if (endOdometer != null) {
                if (!Objects.equals(endTime, input.offTime()) || !Objects.equals(endOdometer, input.odometer())
                        || !Objects.equals(offLatitude, input.latitude())
                        || !Objects.equals(offLongitude, input.longitude())) throw new TripObservationConflictException();
                return false;
            }
            endTime = input.offTime();
            endOdometer = input.odometer();
            offLatitude = input.latitude();
            offLongitude = input.longitude();
            updateActive(true);
        }
        distanceMeters = distanceFrom(startOdometer, endOdometer);
        return true;
    }

    public TripDistanceStatus getDistanceStatus() {
        if (identityStartTime == null) return TripDistanceStatus.LEGACY_UNVERIFIED;
        if (startOdometer == null) return TripDistanceStatus.MISSING_ON;
        if (endOdometer == null) return TripDistanceStatus.IN_PROGRESS;
        return distanceMeters == null ? TripDistanceStatus.ODOMETER_REGRESSION : TripDistanceStatus.KNOWN;
    }

    public static Integer distanceFrom(Integer start, Integer end) {
        return start == null || end == null || start < 0 || end < start ? null : end - start;
    }
}
