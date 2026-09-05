package org.thisway.vehicle.log.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class GpsEventFingerprintTest {
    private final LocalDateTime time = LocalDateTime.of(2026, 9, 5, 0, 0);

    @Test
    void 저장_정밀도와_음수영점을_정규화한다() {
        assertThat(GpsEventFingerprint.of(data(1, "mdn", 0.0, time, 10)))
                .containsExactly(GpsEventFingerprint.of(data(1, "mdn", -0.0, time.withNano(999), 10)));
    }

    @Test
    void 차량_장치_시각_측정값이_다르면_다른_key다() {
        byte[] key = GpsEventFingerprint.of(data(1, "mdn", 0, time, 10));
        assertThat(GpsEventFingerprint.of(data(2, "mdn", 0, time, 10))).isNotEqualTo(key);
        assertThat(GpsEventFingerprint.of(data(1, "other", 0, time, 10))).isNotEqualTo(key);
        assertThat(GpsEventFingerprint.of(data(1, "mdn", 0, time.plusSeconds(1), 10))).isNotEqualTo(key);
        assertThat(GpsEventFingerprint.of(data(1, "mdn", 0, time, 11))).isNotEqualTo(key);
    }

    private GpsLogData data(long vehicle, String mdn, double lat, LocalDateTime occurred, int speed) {
        return new GpsLogData(vehicle, mdn, GpsStatus.NORMAL, lat, 127.0, 90, speed, 100, 12, occurred);
    }
}
