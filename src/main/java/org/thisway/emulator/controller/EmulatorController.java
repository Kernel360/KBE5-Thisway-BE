package org.thisway.emulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.emulator.controller.dto.request.EmulatorRegisterRequest;
import org.thisway.emulator.controller.dto.request.EmulatorUpdateRequest;
import org.thisway.emulator.controller.dto.response.EmulatorDetailResponse;
import org.thisway.emulator.controller.dto.response.EmulatorsResponse;
import org.thisway.emulator.service.EmulatorService;
import org.thisway.emulator.service.dto.output.EmulatorOutput;

@Slf4j
@RestController
@RequestMapping("/api/emulators")
@RequiredArgsConstructor
public class EmulatorController {

    private final EmulatorService emulatorService;

    @PostMapping
    public ResponseEntity<Void> registerEmulator(@Validated @RequestBody EmulatorRegisterRequest request) {
        emulatorService.registerEmulator(request.toEmulatorRegisterInput());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmulatorDetailResponse> getEmulatorDetail(@PathVariable Long id) {
        EmulatorOutput output = emulatorService.getEmulatorDetail(id);
        return ResponseEntity.status(HttpStatus.OK).body(EmulatorDetailResponse.from(output));
    }

    @GetMapping
    public ResponseEntity<EmulatorsResponse> getEmulators(@PageableDefault Pageable pageable) {
        EmulatorsResponse response = EmulatorsResponse.from(emulatorService.getEmulators(pageable));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateEmulator(@PathVariable Long id,
                                               @RequestBody EmulatorUpdateRequest request) {
        emulatorService.updateEmulator(request.toEmulatorUpdateInput(id));
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmulator(@PathVariable Long id) {
        emulatorService.deleteEmulator(id);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
