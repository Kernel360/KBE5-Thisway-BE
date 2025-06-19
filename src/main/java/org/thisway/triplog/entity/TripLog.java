package org.thisway.triplog.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.common.BaseEntity;
import org.thisway.vehicle.entity.Vehicle;

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

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private Integer totalTripMeter;

    @Column(nullable = false)
    private Double onLatitude;

    @Column(nullable = false)
    private Double onLongitude;

    @Column(nullable = false)
    private String onAddr;

    @Column
    private String onAddrDetail;

    @Column(nullable = false)
    private Double offLatitude;

    @Column(nullable = false)
    private Double offLongitude;

    @Column(nullable = false)
    private String offAddr;

    @Column
    private String offAddrDetail;

    @Builder
    public TripLog (
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
            String offAddrDetail
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
    }

}
