package org.thisway.vehicle.triplog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.common.BaseEntity;
import org.thisway.vehicle.domain.Vehicle;

import java.time.LocalDateTime;

@Entity
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

    public void finishTrip(
            LocalDateTime offTime,
            Integer totalTripMeter,
            Double offLatitude,
            Double offLongitude,
            String offAddr,
            String offAddrDetail
    ) {
        this.endTime = offTime;
        this.totalTripMeter = totalTripMeter;
        this.offLatitude = offLatitude;
        this.offLongitude = offLongitude;
        this.offAddr = offAddr;
        this.offAddrDetail = offAddrDetail;
        this.updateActive(true);
    }
}
