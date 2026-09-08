package org.thisway.emulator.credential;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/emulators/{id}/device-key")
@RequiredArgsConstructor
public class DeviceCredentialController {
    private final DeviceCredentialService service;

    @PostMapping
    public ResponseEntity<IssuedDeviceKey> issue(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.issue(id));
    }

    @DeleteMapping
    public ResponseEntity<Void> revoke(@PathVariable long id) {
        service.revoke(id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping
    public ResponseEntity<DeviceCredentialService.Status> status(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(id));
    }
}
