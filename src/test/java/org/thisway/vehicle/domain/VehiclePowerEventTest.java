package org.thisway.vehicle.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class VehiclePowerEventTest {
    private final LocalDateTime time = LocalDateTime.of(2020, 1, 1, 10, 0);

    @Test
    void 과거_OFF는_최신_ON의_상태와_좌표를_덮지_않는다() {
        var vehicle = Vehicle.builder().mileage(0).build();
        assertThat(vehicle.observePowerEvent(time, true, 37.5, 127.0)).isTrue();
        assertThat(vehicle.observePowerEvent(time.minusHours(1), false, 38.0, 128.0)).isFalse();
        assertThat(vehicle.isPowerOn()).isTrue();
        assertThat(vehicle.getLastPowerEventTime()).isEqualTo(time);
        assertThat(vehicle.getLatitude()).isEqualTo(37.5);
        assertThat(vehicle.getLongitude()).isEqualTo(127.0);
    }

    @Test
    void 과거_ON도_최신_OFF를_다시_켜지_못한다() {
        var vehicle = Vehicle.builder().mileage(0).build();
        vehicle.observePowerEvent(time, false, 37.5, 127.0);
        assertThat(vehicle.observePowerEvent(time.minusSeconds(1), true, 38.0, 128.0)).isFalse();
        assertThat(vehicle.isPowerOn()).isFalse();
        assertThat(vehicle.getLatitude()).isEqualTo(37.5);
    }

    @Test
    void 같은시각은_도착순서와_무관하게_OFF가_우선한다() {
        for (boolean firstOn : new boolean[]{true, false}) {
            var vehicle = Vehicle.builder().mileage(0).build();
            vehicle.observePowerEvent(time, firstOn, firstOn ? 38.0 : 37.5, 127.0);
            vehicle.observePowerEvent(time, !firstOn, firstOn ? 37.5 : 38.0, 127.0);
            assertThat(vehicle.isPowerOn()).isFalse();
            assertThat(vehicle.getLatitude()).isEqualTo(37.5);
            assertThat(vehicle.getLastPowerEventTime()).isEqualTo(time);
        }
    }

    @Test
    void 같은종류와_시각의_재수신은_좌표를_바꾸지_않는다() {
        for (boolean on : new boolean[]{true, false}) {
            var vehicle = Vehicle.builder().mileage(0).build();
            vehicle.observePowerEvent(time, on, 37.5, 127.0);
            assertThat(vehicle.observePowerEvent(time, on, 38.0, 128.0)).isFalse();
            assertThat(vehicle.getLatitude()).isEqualTo(37.5);
        }
    }

    @Test
    void 더_새로운_이벤트는_상태와_좌표와_시각을_함께_갱신한다() {
        var vehicle = Vehicle.builder().mileage(0).powerOn(true).latitude(36.0).build();
        assertThat(vehicle.getLastPowerEventTime()).isNull();
        vehicle.observePowerEvent(time, false, 37.5, 127.0);
        vehicle.observePowerEvent(time.plusSeconds(1), true, 38.0, 128.0);
        assertThat(vehicle.isPowerOn()).isTrue();
        assertThat(vehicle.getLatitude()).isEqualTo(38.0);
        assertThat(vehicle.getLongitude()).isEqualTo(128.0);
        assertThat(vehicle.getLastPowerEventTime()).isEqualTo(time.plusSeconds(1));
    }

    @Test
    void 이벤트시각이_없으면_상태를_변경하지_않는다() {
        var vehicle = Vehicle.builder().mileage(0).powerOn(true).build();
        assertThatThrownBy(() -> vehicle.observePowerEvent(null, false, 37.5, 127.0))
                .isInstanceOf(NullPointerException.class);
        assertThat(vehicle.isPowerOn()).isTrue();
        assertThat(vehicle.getLastPowerEventTime()).isNull();
    }
}
