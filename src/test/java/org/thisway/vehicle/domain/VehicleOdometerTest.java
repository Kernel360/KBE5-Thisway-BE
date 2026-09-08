package org.thisway.vehicle.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VehicleOdometerTest {
    @Test
    void 누적값은_더하지_않고_중복과_낮은_지연값에도_감소하지_않는다() {
        var vehicle = Vehicle.builder().mileage(1000).build();
        vehicle.observeOdometer(1500);
        vehicle.observeOdometer(1500);
        vehicle.observeOdometer(1200);
        assertThat(vehicle.getMileage()).isEqualTo(1500);
        vehicle.observeOdometer(2000);
        assertThat(vehicle.getMileage()).isEqualTo(2000);
    }

    @Test
    void 잘못된_관측값은_상태를_바꾸지_않는다() {
        var vehicle = Vehicle.builder().mileage(1000).build();
        assertThatThrownBy(() -> vehicle.observeOdometer(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vehicle.observeOdometer(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(vehicle.getMileage()).isEqualTo(1000);
    }
}
