package org.thisway.emulator.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.emulator.domain.Emulator;
import org.thisway.emulator.infrastructure.EmulatorRepository;
import org.thisway.vehicle.domain.VehicleReference;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.infrastructure.VehicleRepository;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EmulatorService {

    private final EmulatorRepository emulatorRepository;
    private final VehicleRepository vehicleRepository;

    public void registerEmulator(EmulatorCreateInput request) {

        if (emulatorRepository.findByMdn(request.mdn()).isPresent()) {
            throw new CustomException(ErrorCode.EMULATOR_ALREADY_EXIST);
        }

        Vehicle vehicle = vehicleRepository.findByIdAndActiveTrue(request.vehicleId())
                .orElseThrow(() -> new CustomException(ErrorCode.VEHICLE_NOT_FOUND));

        Emulator emulator = Emulator.builder()
                .mdn(request.mdn())
                .vehicle(vehicle)
                .terminalId(request.terminalId())
                .manufactureId(request.manufactureId())
                .packetVersion(request.packetVersion())
                .deviceId(request.deviceId())
                .deviceFirmwareVersion(request.deviceFirmwareVersion())
                .build();

        emulatorRepository.save(emulator);
    }

    @Transactional(readOnly = true)
    public EmulatorOutput getEmulatorDetail(Long id) {
        Emulator emulator = emulatorRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.EMULATOR_NOT_FOUND));

        return EmulatorOutput.from(emulator);
    }

    @Transactional(readOnly = true)
    public EmulatorsOutput getEmulators(Pageable pageable) {
        Page<Emulator> emulators = emulatorRepository.findAll(pageable);

        return EmulatorsOutput.from(emulators);
    }

    public void updateEmulator(EmulatorUpdateInput input) {

        if (input.isEmpty()) {
            throw new CustomException(ErrorCode.EMULATOR_EMPTY_UPDATE_REQUEST);
        }

        Emulator emulator = emulatorRepository.findById(input.id())
                .orElseThrow(() -> new CustomException(ErrorCode.EMULATOR_NOT_FOUND));

        if (input.mdn() != null && !input.mdn().equals(emulator.getMdn()) &&
                emulatorRepository.findByMdn(input.mdn()).isPresent()) {
            throw new CustomException(ErrorCode.EMULATOR_ALREADY_EXIST);
        }

        Vehicle vehicle = emulator.getVehicle();
        if (input.vehicleId() != null && !input.vehicleId().equals(vehicle.getId())) {
            vehicle = vehicleRepository.findByIdAndActiveTrue(input.vehicleId())
                    .orElseThrow(() -> new CustomException(ErrorCode.VEHICLE_NOT_FOUND));
        }

        emulator.update(
                input.mdn(),
                vehicle,
                input.terminalId(),
                input.manufactureId(),
                input.packetVersion(),
                input.deviceId(),
                input.deviceFirmwareVersion()
        );
    }

    public void deleteEmulator(Long id) {
        Emulator emulator = emulatorRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.EMULATOR_NOT_FOUND));

        emulatorRepository.delete(emulator);
    }

    @Transactional(readOnly = true)
    public VehicleReference getVehicleReferenceByMdn(String mdn) {
        return emulatorRepository.findVehicleByMdn(mdn)
                .orElseThrow(() -> new CustomException(ErrorCode.EMULATOR_NOT_FOUND));
    }
}
