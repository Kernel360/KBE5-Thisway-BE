package org.thisway.vehicle.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.support.common.BaseEntity;
import org.thisway.company.domain.Company;
import org.thisway.vehicle.interfaces.VehicleUpdateRequest;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vehicle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_model_id", nullable = false)
    private VehicleModel vehicleModel;

    @Column(nullable = false)
    private String carNumber;

    @Column(nullable = false)
    private String color;

    @Column(nullable = false)
    private Integer mileage;

    @Column(nullable = false)
    private boolean powerOn;

    private Double latitude;

    private Double longitude;

    @Builder
    public Vehicle(
            Company company,
            VehicleModel vehicleModel,
            String carNumber,
            String color,
            Integer mileage,
            boolean powerOn,
            Double latitude,
            Double longitude
    ) {
        this.company = company;
        this.vehicleModel = vehicleModel;
        this.carNumber = carNumber;
        this.color = color;
        this.mileage = mileage;
        this.powerOn = powerOn;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void update(VehicleUpdateRequest request, VehicleModel vehicleModel) {
        if (request.carNumber() != null) {
            this.carNumber = request.carNumber();
        }
        if (request.color() != null) {
            this.color = request.color();
        }
        if (vehicleModel != null) {
            this.vehicleModel = vehicleModel;
        }
    }

    public void updatePowerOn(boolean powerOn) {
        this.powerOn = powerOn;
    }

    public void updateMileage(Integer additionalMileage) {
        if (additionalMileage != null && additionalMileage > 0) {
            this.mileage += additionalMileage;
        }
    }

    public void updateLocation(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
