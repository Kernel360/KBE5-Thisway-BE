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
public class VehicleModel extends BaseEntity {

    @Column(nullable = false)
    private String manufacturer;

    @Column(nullable = false)
    private Integer modelYear;

    @Column(nullable = false)
    private String model;

    @Builder
    public VehicleModel(
            String manufacturer,
            Integer modelYear,
            String model
    ) {
        this.manufacturer = manufacturer;
        this.modelYear = modelYear;
        this.model = model;
    }

    public void partialUpdate(String manufacturer, Integer modelYear, String model) {
        if (manufacturer != null) {
            this.manufacturer = manufacturer;
        }
        if (modelYear != null) {
            this.modelYear = modelYear;
        }
        if (model != null) {
            this.model = model;
        }
    }
}
