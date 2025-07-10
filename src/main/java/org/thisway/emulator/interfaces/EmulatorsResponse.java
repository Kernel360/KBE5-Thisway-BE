package org.thisway.emulator.interfaces;

import java.util.List;

import org.thisway.common.PageInfo;
import org.thisway.emulator.application.EmulatorsOutput;

public record EmulatorsResponse(
        List<EmulatorDetailResponse> emulators,
        PageInfo pageInfo
) {
    public static EmulatorsResponse from(EmulatorsOutput emulatorsOutput) {
        List<EmulatorDetailResponse> emulators = emulatorsOutput.emulators().stream()
                .map(EmulatorDetailResponse::from)
                .toList();
        return new EmulatorsResponse(emulators, emulatorsOutput.pageInfo());
    }
}
