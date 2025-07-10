package org.thisway.vehicle.vehicle_model.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.support.common.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleModel extends BaseEntity {

    @Column(nullable = false)
    private String manufacturer;

    @Column(nullable = false)
    private Integer modelYear;

    @Column(nullable = false)
    private String name;

    @Builder
    public VehicleModel(
            String manufacturer,
            Integer modelYear,
            String name
    ) {
        this.manufacturer = manufacturer;
        this.modelYear = modelYear;
        this.name = name;
    }

    public void partialUpdate(String manufacturer, Integer modelYear, String name) {
        if (manufacturer != null) {
            this.manufacturer = manufacturer;
        }
        if (modelYear != null) {
            this.modelYear = modelYear;
        }
        if (name != null) {
            this.name = name;
        }
    }
}

