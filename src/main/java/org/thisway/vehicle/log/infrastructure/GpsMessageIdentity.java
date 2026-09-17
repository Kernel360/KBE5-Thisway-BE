package org.thisway.vehicle.log.infrastructure;

import org.thisway.emulator.credential.DeviceIdentity;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import java.util.Map;

/** Server-only AMQP metadata. Broker credentials and publish ACLs are a required trust boundary. */
public final class GpsMessageIdentity {
    private GpsMessageIdentity() {}

    public static Map<String, Object> headers(DeviceIdentity identity) {
        return Map.of("thisway-identity-version", 1, "thisway-emulator-id", identity.emulatorId(),
                "thisway-vehicle-id", identity.vehicleId(), "thisway-company-id", identity.companyId(),
                "thisway-assignment-revision", identity.assignmentRevision());
    }

    public static DeviceIdentity read(Map<String, Object> headers, String mdn) {
        if (headers == null || number(headers, "thisway-identity-version", 1) != 1
                || mdn == null || mdn.isBlank() || mdn.length() > 20) throw rejected();
        return new DeviceIdentity(number(headers, "thisway-emulator-id", 1),
                number(headers, "thisway-vehicle-id", 1), number(headers, "thisway-company-id", 1), mdn,
                number(headers, "thisway-assignment-revision", 0));
    }

    private static long number(Map<String, Object> headers, String name, long minimum) {
        Object value = headers.get(name);
        if (!(value instanceof Long) && !(value instanceof Integer)) throw rejected();
        long result = ((Number) value).longValue();
        if (result < minimum) throw rejected();
        return result;
    }

    private static CustomException rejected() {
        return new CustomException(ErrorCode.DEVICE_AUTHENTICATION_FAILED);
    }
}
