package org.thisway.vehicle.vehicle_model.interfaces;

import java.util.List;

import org.springframework.data.domain.Page;
import org.thisway.support.common.PageInfo;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;

public record VehicleModelsResponse(

        List<VehicleModelResponse> vehicleModels,

        PageInfo pageInfo
) {

    public static VehicleModelsResponse from(Page<VehicleModel> vehicleModelPage) {
        List<VehicleModelResponse> vehicleModels = vehicleModelPage.getContent().stream()
                .map(VehicleModelResponse::from)
                .toList();

        PageInfo pageInfo = PageInfo.from(vehicleModelPage);

        return new VehicleModelsResponse(vehicleModels, pageInfo);
    }

}
