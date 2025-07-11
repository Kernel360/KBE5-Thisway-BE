package org.thisway.emulator.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.emulator.application.EmulatorCreateInput;

public record EmulatorRegisterRequest(
        @NotBlank
        String mdn,

        @NotNull
        Long vehicleId,

        @NotBlank
        String terminalId,

        @NotNull
        Integer manufactureId,

        @NotNull
        Integer packetVersion,

        @NotNull
        Integer deviceId,

        @NotBlank
        String deviceFirmwareVersion
) {
    public EmulatorCreateInput toEmulatorRegisterInput() {
        return EmulatorCreateInput.builder()
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
