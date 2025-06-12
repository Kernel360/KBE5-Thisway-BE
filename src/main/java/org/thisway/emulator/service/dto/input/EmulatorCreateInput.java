package org.thisway.emulator.service.dto.input;

import lombok.Builder;

@Builder
public record EmulatorCreateInput(
        String mdn,
        Long vehicleId,
        String terminalId,
        Integer manufactureId,
        Integer packetVersion,
        Integer deviceId,
        String deviceFirmwareVersion
) {
}
