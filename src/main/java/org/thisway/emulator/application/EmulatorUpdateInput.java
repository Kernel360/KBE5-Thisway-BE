package org.thisway.emulator.application;

import lombok.Builder;

@Builder
public record EmulatorUpdateInput(
        Long id,
        String mdn,
        Long vehicleId,
        String terminalId,
        Integer manufactureId,
        Integer packetVersion,
        Integer deviceId,
        String deviceFirmwareVersion
) {
    public boolean isEmpty() {
        return mdn == null &&
                vehicleId == null &&
                terminalId == null &&
                manufactureId == null &&
                packetVersion == null &&
                deviceId == null &&
                deviceFirmwareVersion == null;
    }
}
