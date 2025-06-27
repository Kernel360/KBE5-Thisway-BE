package org.thisway.vehicle.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.repository.LogRepository;
import org.thisway.vehicle.dto.response.VehicleTrackResponse;
import org.thisway.vehicle.dto.response.VehicleTracksResponse;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.repository.VehicleRepository;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VehicleTrackService implements VehicleTrackClient {

    private final LogRepository logRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    public VehicleTracksResponse trackVehicles(long companyId, Pageable pageable) {
        Page<Vehicle> vehiclePage = vehicleRepository.findAllByCompanyIdAndPowerOnIsAndActiveTrue(companyId, true, pageable);

        List<Long> vehicleIds = vehiclePage.getContent().stream()
                .map(Vehicle::getId)
                .toList();

        Map<Long, GpsLogData> gpsMap = logRepository.findCurrentGpsByVehicleIds(vehicleIds);

        List<VehicleTrackResponse> trackResponses = vehiclePage.getContent().stream()
                .map(vehicle -> {
                    GpsLogData gps = gpsMap.get(vehicle.getId());
                    return new VehicleTrackResponse(
                            vehicle.getId(),
                            vehicle.getCarNumber(),
                            vehicle.isPowerOn(),
                            gps != null ? gps.latitude() : null,
                            gps != null ? gps.longitude() : null,
                            gps != null ? gps.angle() : null
                    );
                })
                .toList();

        Page<VehicleTrackResponse> trackPage = new PageImpl<>(
                trackResponses,
                vehiclePage.getPageable(),
                vehiclePage.getTotalElements()
        );

        return VehicleTracksResponse.from(trackPage);
    }
}
