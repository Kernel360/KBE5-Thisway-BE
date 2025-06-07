package org.thisway.vehicle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.common.BaseEntity;
import org.thisway.company.entity.Company;
import org.thisway.vehicle.dto.request.VehicleUpdateRequest;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vehicle extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne
    @JoinColumn(name = "vehicle_detail_id", nullable = false)
    private VehicleDetail vehicleDetail;

    @Column(nullable = false)
    private String carNumber;

    @Column(nullable = false)
    private String color;

    private Integer mileage;

    @Column(nullable = false)
    private boolean powerOn;

    private Double latitude;

    private Double longitude;

    @Builder
    public Vehicle(
            Company company,
            VehicleDetail vehicleDetail,
            String carNumber,
            String color,
            Integer mileage,
            boolean powerOn,
            Double latitude,
            Double longitude
    ) {
        this.company = company;
        this.vehicleDetail = vehicleDetail;
        this.carNumber = carNumber;
        this.color = color;
        this.mileage = mileage;
        this.powerOn = powerOn;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void update(VehicleUpdateRequest request) {
        partialUpdate(request.carNumber(), request.color());

        if (request.manufacturer() != null || request.modelYear() != null || request.model() != null) {
            this.vehicleDetail.partialUpdate(request.manufacturer(), request.modelYear(), request.model());
        }
    }

    public void partialUpdate(String carNumber, String color) {
        if (carNumber != null) {
            this.carNumber = carNumber;
        }
        if (color != null) {
            this.color = color;
        }
    }

    public void updatePowerOn(boolean powerOn) {
        this.powerOn = powerOn;
    }
}
