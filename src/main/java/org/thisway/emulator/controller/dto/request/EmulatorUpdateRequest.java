package org.thisway.emulator.controller.dto.request;

import org.thisway.emulator.service.dto.input.EmulatorUpdateInput;

public record EmulatorUpdateRequest(
        String mdn,
        Long vehicleId,
        String terminalId,
        Integer manufactureId,
        Integer packetVersion,
        Integer deviceId,
        String deviceFirmwareVersion
) {
    public EmulatorUpdateInput toEmulatorUpdateInput(Long id) {
        return EmulatorUpdateInput.builder()
                .id(id)
                .mdn(mdn)
                .vehicleId(vehicleId)
                .terminalId(terminalId)
                .manufactureId(manufactureId)
                .packetVersion(packetVersion)
                .deviceId(deviceId)
                .deviceFirmwareVersion(deviceFirmwareVersion)
                .build();
    }
}
