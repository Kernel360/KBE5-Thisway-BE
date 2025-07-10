package org.thisway.emulator.application;

import org.springframework.data.domain.Page;
import org.thisway.common.PageInfo;
import org.thisway.emulator.domain.Emulator;

import java.util.List;

public record EmulatorsOutput(
        List<EmulatorOutput> emulators,
        PageInfo pageInfo
) {
    public static EmulatorsOutput from(Page<Emulator> emulatorPage) {
        List<EmulatorOutput> emulators = emulatorPage.map(EmulatorOutput::from).toList();
        PageInfo pageInfo = PageInfo.from(emulatorPage);

        return new EmulatorsOutput(emulators, pageInfo);
    }
}
