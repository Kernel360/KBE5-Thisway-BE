package org.thisway.vehicle.domain;

import org.thisway.company.domain.Company;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;

public class VehicleFixture {

    public static Vehicle createVehicle(String carNumber, Company company, VehicleModel model) {
        return Vehicle.builder()
                .company(company)
                .vehicleModel(model)
                .carNumber(carNumber)
                .color("흰색")
                .mileage(0)
                .powerOn(false)
                .latitude(null)
                .longitude(null)
                .build();
    }
}
