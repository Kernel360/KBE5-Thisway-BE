package org.thisway.emulator.credential;

import java.time.Instant;

/** The secret is deliberately exposed only by the issuance HTTP response, never by toString. */
public record IssuedDeviceKey(String key, Instant expiresAt) {
    @Override public String toString() { return "IssuedDeviceKey[key=REDACTED, expiresAt=" + expiresAt + "]"; }
}
