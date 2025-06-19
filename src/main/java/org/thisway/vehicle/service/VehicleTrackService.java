package org.thisway.vehicle.service;

import java.util.List;
import java.util.Optional;
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

@Service
@RequiredArgsConstructor
public class VehicleTrackService implements VehicleTrackClient {

    private final LogRepository logRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    public VehicleTracksResponse trackVehicles(long companyId, Pageable pageable) {
        Page<Vehicle> vehiclePage = vehicleRepository.findAllByCompanyIdAndActiveTrue(companyId, pageable);

        List<VehicleTrackResponse> trackResponses = vehiclePage.getContent().stream()
                .map(vehicle -> {
                    Optional<GpsLogData> gps = logRepository.findCurrentGpsByVehicleId(vehicle.getId());
                    return new VehicleTrackResponse(
                            vehicle.getId(),
                            vehicle.getCarNumber(),
                            vehicle.isPowerOn(),
                            gps.map(GpsLogData::latitude).orElse(null),
                            gps.map(GpsLogData::longitude).orElse(null),
                            gps.map(GpsLogData::angle).orElse(null)
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
