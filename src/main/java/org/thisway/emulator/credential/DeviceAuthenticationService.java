package org.thisway.emulator.credential;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class DeviceAuthenticationService {
    private static final Pattern KEY_FORMAT = Pattern.compile("twdev_[A-Za-z0-9_-]{43}");
    private static final byte[] ABSENT_HASH = "0".repeat(64).getBytes(StandardCharsets.US_ASCII);
    private final DeviceCredentialRepository repository;

    public DeviceAuthenticationService(DeviceCredentialRepository repository) {
        this.repository = repository;
    }

    /** Does not install HTTP authentication or authorize a later asynchronous DB write. */
    @Transactional(readOnly = true)
    public DeviceIdentity authenticate(long emulatorId, String key, String payloadMdn) {
        if (emulatorId <= 0 || key == null || key.length() != 49 || !KEY_FORMAT.matcher(key).matches()
                || payloadMdn == null || payloadMdn.isBlank() || payloadMdn.length() > 20) {
            throw rejected();
        }
        var candidate = repository.findAuthenticationCandidate(emulatorId, Instant.now());
        byte[] suppliedHash = DeviceKeyMaterial.hash(key).getBytes(StandardCharsets.US_ASCII);
        byte[] expectedHash = candidate.map(value -> value.keyHash().getBytes(StandardCharsets.US_ASCII))
                .orElse(ABSENT_HASH);
        boolean matches = MessageDigest.isEqual(expectedHash, suppliedHash);
        if (!matches || candidate.isEmpty() || !candidate.get().identity().mdn().equals(payloadMdn)) {
            throw rejected();
        }
        return candidate.get().identity();
    }

    private static CustomException rejected() {
        return new CustomException(ErrorCode.DEVICE_AUTHENTICATION_FAILED);
    }
}
