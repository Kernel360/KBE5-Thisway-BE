package org.thisway.emulator.application;

import org.thisway.emulator.domain.Emulator;

public record EmulatorOutput(
        Long id,
        String mdn,
        Long vehicleId,
        String terminalId,
        Integer manufactureId,
        Integer packetVersion,
        Integer deviceId,
        String deviceFirmwareVersion
) {
    public static EmulatorOutput from(Emulator emulator) {
        return new EmulatorOutput(
                emulator.getId(),
                emulator.getMdn(),
                emulator.getVehicle().getId(),
                emulator.getTerminalId(),
                emulator.getManufactureId(),
                emulator.getPacketVersion(),
                emulator.getDeviceId(),
                emulator.getDeviceFirmwareVersion()
        );
    }
}
