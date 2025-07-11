package org.thisway.emulator.interfaces;

import org.thisway.emulator.application.EmulatorOutput;

public record EmulatorDetailResponse(
        Long id,
        String mdn,
        Long vehicleId,
        String terminalId,
        Integer manufactureId,
        Integer packetVersion,
        Integer deviceId,
        String deviceFirmwareVersion
) {
    public static EmulatorDetailResponse from(EmulatorOutput output) {
        return new EmulatorDetailResponse(
                output.id(),
                output.mdn(),
                output.vehicleId(),
                output.terminalId(),
                output.manufactureId(),
                output.packetVersion(),
                output.deviceId(),
                output.deviceFirmwareVersion()
        );
    }
}
