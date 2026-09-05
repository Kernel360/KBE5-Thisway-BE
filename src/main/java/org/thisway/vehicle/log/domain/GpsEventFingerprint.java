package org.thisway.vehicle.log.domain;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** v1 identity: complete normalized observation, not a device-provided event ID. */
public final class GpsEventFingerprint {
    private GpsEventFingerprint() {}

    public static byte[] of(GpsLogData data) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.writeInt(1);
            output.writeLong(data.vehicleId());
            text(output, data.mdn());
            text(output, data.occurredTime().withNano(0).toString());
            text(output, data.gpsStatus().getCode());
            // MySQL DOUBLE treats positive and negative zero as the same value.
            output.writeDouble(data.latitude() == 0 ? 0 : data.latitude());
            output.writeDouble(data.longitude() == 0 ? 0 : data.longitude());
            output.writeInt(data.angle());
            output.writeInt(data.speed());
            output.writeInt(data.totalTripMeter());
            output.writeInt(data.batteryVoltage());
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("GPS fingerprint generation failed", exception);
        }
    }

    private static void text(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
