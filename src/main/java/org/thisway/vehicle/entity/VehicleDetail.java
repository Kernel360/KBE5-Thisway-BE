package org.thisway.vehicle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.common.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleDetail extends BaseEntity {

    @Column(nullable = false)
    private String manufacturer;

    @Column(nullable = false)
    private Integer modelYear;

    @Column(nullable = false)
    private String model;

    @Builder
    public VehicleDetail(
            String manufacturer,
            Integer modelYear,
            String model
    ) {
        this.manufacturer = manufacturer;
        this.modelYear = modelYear;
        this.model = model;
    }

}
