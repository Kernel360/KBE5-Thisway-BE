package org.thisway.vehicle.triplog.domain;

import org.junit.jupiter.api.Test;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.log.domain.GpsLogData;
import org.thisway.vehicle.log.domain.GpsStatus;
import org.thisway.vehicle.triplog.interfaces.TripLogDetailResponse;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TripObservationTest {
    private final Vehicle vehicle = mock(Vehicle.class);
    private final LocalDateTime start = LocalDateTime.of(2020, 1, 1, 10, 0);
    private final LocalDateTime end = start.plusHours(1);

    @Test
    void ON_OFF의_순서와_재전송에_무관하게_같은_종료운행과_500미터가_된다() {
        for (boolean offFirst : List.of(false, true)) {
            var trip = TripLog.observed(vehicle, start);
            var on = input(null, 1000);
            var off = input(end, 1500);
            assertThat(trip.observe(offFirst ? off : on)).isTrue();
            assertThat(trip.observe(offFirst ? on : off)).isTrue();
            assertThat(trip.observe(on)).isFalse();
            assertThat(trip.observe(off)).isFalse();
            assertThat(trip.getStartOdometer()).isEqualTo(1000);
            assertThat(trip.getEndOdometer()).isEqualTo(1500);
            assertThat(trip.getDistanceMeters()).isEqualTo(500);
            assertThat(trip.getDistanceStatus()).isEqualTo(TripDistanceStatus.KNOWN);
            assertThat(trip.getEndTime()).isEqualTo(end);
            assertThat(trip.isActive()).isTrue();
        }
    }

    @Test
    void OFF만_받은_거리는_0이_아닌_미확정이고_늦은_ON이_종료를_취소하지_않는다() {
        var trip = TripLog.observed(vehicle, start);
        trip.observe(input(end, 1500));
        assertThat(trip.getDistanceMeters()).isNull();
        assertThat(trip.getDistanceStatus()).isEqualTo(TripDistanceStatus.MISSING_ON);
        trip.observe(input(null, 1000));
        assertThat(trip.getEndTime()).isEqualTo(end);
        assertThat(trip.isActive()).isTrue();
    }

    @Test
    void 계기값_감소는_음수거리나_0으로_꾸미지_않고_실제_0과_구분한다() {
        for (int endReading : List.of(900, 1000)) {
            var trip = TripLog.observed(vehicle, start);
            trip.observe(input(null, 1000));
            trip.observe(input(end, endReading));
            assertThat(trip.getDistanceMeters()).isEqualTo(endReading == 900 ? null : 0);
            assertThat(trip.getDistanceStatus()).isEqualTo(endReading == 900
                    ? TripDistanceStatus.ODOMETER_REGRESSION : TripDistanceStatus.KNOWN);
        }
    }

    @Test
    void 같은_운행관측의_계기값_좌표_종료시각_충돌은_원래값을_보존한다() {
        var trip = TripLog.observed(vehicle, start);
        trip.observe(input(null, 1000));
        trip.observe(input(end, 1500));
        for (var conflict : List.of(input(null, 999), input(end, 1600), input(end.plusSeconds(1), 1500),
                new TripLogSaveInput(vehicle, "fixture", start, null, 38.0, 127.0, 1000))) {
            assertThatThrownBy(() -> trip.observe(conflict)).isInstanceOf(TripObservationConflictException.class);
        }
        assertThat(trip.getEndTime()).isEqualTo(end);
        assertThat(trip.getDistanceMeters()).isEqualTo(500);
        assertThat(trip.getOnLatitude()).isEqualTo(37.5);
    }

    @Test
    void legacy_혼합거리값은_보존하되_검증된_거리로_응답하지_않는다() {
        var trip = TripLog.builder().vehicle(vehicle).startTime(start).endTime(end)
                .totalTripMeter(1500).active(true).build();
        assertThatThrownBy(() -> trip.observe(input(null, 1000))).isInstanceOf(TripObservationConflictException.class);
        assertThat(trip.getTotalTripMeter()).isEqualTo(1500);
        var response = TripLogDetailResponse.from(vehicle, trip, 0.0);
        assertThat(response.tripMeter()).isNull();
        assertThat(response.distanceStatus()).isEqualTo(TripDistanceStatus.LEGACY_UNVERIFIED);
        assertThat(TripLogBriefInfo.from(trip).tripMeter()).isNull();
    }

    @Test
    void 현재_GPS_누적값은_시작계기값을_빼고_누락과_역전은_미확정이다() {
        var trip = TripLog.observed(vehicle, start);
        trip.observe(input(null, 1000));
        assertThat(CurrentDrivingInfo.from(trip, null).tripMeter()).isNull();
        assertThat(CurrentDrivingInfo.from(trip, gps(1500)).tripMeter()).isEqualTo(500);
        assertThat(CurrentDrivingInfo.from(trip, gps(900)).tripMeter()).isNull();
        assertThat(CurrentDrivingInfo.from(trip, gps(1500)).startOdometer()).isEqualTo(1000);
    }

    @Test
    void 잘못된_시간_좌표_계기값은_상태변경_전에_거부한다() {
        var trip = TripLog.observed(vehicle, start);
        for (var invalid : List.of(input(end, -1), input(start.minusSeconds(1), 1000),
                new TripLogSaveInput(vehicle, "fixture", null, end, 37.5, 127.0, 1000),
                new TripLogSaveInput(vehicle, "fixture", start, null, Double.NaN, 127.0, 1000))) {
            assertThatThrownBy(() -> trip.observe(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(trip.getStartOdometer()).isNull();
        assertThat(trip.getEndOdometer()).isNull();
    }

    private TripLogSaveInput input(LocalDateTime off, int odometer) {
        return new TripLogSaveInput(vehicle, "fixture", start, off, 37.5, 127.0, odometer);
    }

    private GpsLogData gps(int odometer) {
        return new GpsLogData(1L, "fixture", GpsStatus.NORMAL, 37.5, 127.0, 0, 0, odometer, 14, start.plusMinutes(1));
    }
}
