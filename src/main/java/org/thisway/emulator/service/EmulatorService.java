package org.thisway.emulator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.emulator.repository.EmulatorRepository;
import org.thisway.vehicle.api.EmulatorOperations;

@Service
@RequiredArgsConstructor
public class EmulatorService implements EmulatorOperations {

    private final EmulatorRepository emulatorRepository;
    private final RestClient restClient;

    @Override
    public void startEmulator(Long vehicleId, String mdn) {
        validateEmulatorExists(vehicleId);
        callEmulatorApi(mdn, "start");
    }

    @Override
    public void stopEmulator(Long vehicleId, String mdn) {
        validateEmulatorExists(vehicleId);
        callEmulatorApi(mdn, "stop");
    }

    public void callEmulatorApi(String mdn, String action) {
        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/emulator/{mdn}/{action}")
                            .build(mdn, action))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new CustomException(ErrorCode.EMULATOR_API_ERROR);
        }
    }

    public void validateEmulatorExists(Long vehicleId) {
        emulatorRepository.findByVehicleId(vehicleId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMULATOR_NOT_FOUND));
    }
}
