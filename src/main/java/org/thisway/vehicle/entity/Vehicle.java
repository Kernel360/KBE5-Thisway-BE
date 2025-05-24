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

    @Column(nullable = false)
    private Integer mileage;

    @Column(nullable = false)
    private boolean isOn;

    private Double latitude;

    private Double longitude;

    @Builder
    public Vehicle(
            Company company,
            VehicleDetail vehicleDetail,
            String carNumber,
            String color,
            Integer mileage,
            boolean isOn,
            Double latitude,
            Double longitude
    ) {
        this.company = company;
        this.vehicleDetail = vehicleDetail;
        this.carNumber = carNumber;
        this.color = color;
        this.mileage = mileage;
        this.isOn = isOn;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
