package org.thisway.vehicle.support;

import org.thisway.company.entity.Company;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleModel;

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
