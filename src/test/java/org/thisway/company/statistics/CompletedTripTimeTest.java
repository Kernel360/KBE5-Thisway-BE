package org.thisway.company.statistics;

import org.junit.jupiter.api.Test;
import org.thisway.company.statistics.domain.CompletedTripTime;
import org.thisway.company.statistics.domain.CompletedTripTime.Interval;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CompletedTripTimeTest {
    final LocalDateTime day = LocalDateTime.of(2020, 1, 1, 0, 0);

    @Test void 중복_겹침_인접구간은_차량별로_합치고_차량사이에는_합산한다() {
        var result = CompletedTripTime.calculate(List.of(
                trip(1, 9, 11), trip(1, 9, 11), trip(1, 10, 12), trip(1, 12, 13), trip(2, 9, 10)), day, 2);
        assertThat(result.minutes()).isEqualTo(300);
        assertThat(result.hourlyRates()[9]).isEqualTo(100);
        assertThat(result.hourlyRates()[10]).isEqualTo(50);
        assertThat(result.hourlyRates()[12]).isEqualTo(50);
    }

    @Test void 자정경계_밖의_시간은_제외하고_끝시간은_다음날에_중복하지_않는다() {
        var result = CompletedTripTime.calculate(List.of(new Interval(1, day.minusHours(1), day.plusMinutes(30)),
                new Interval(1, day.plusHours(23).plusMinutes(30), day.plusDays(1).plusHours(1)),
                new Interval(1, day.minusHours(2), day), new Interval(1, day.plusDays(1), day.plusDays(1).plusHours(2))), day, 1);
        assertThat(result.minutes()).isEqualTo(60);
        assertThat(result.hourlyRates()[0]).isEqualTo(50);
        assertThat(result.hourlyRates()[23]).isEqualTo(50);
    }

    @Test void 미종료_역전_영길이_운행을_추정하지_않고_제외한다() {
        var result = CompletedTripTime.calculate(List.of(new Interval(1, day, null), trip(1, 12, 11), trip(1, 9, 9)), day, 1);
        assertThat(result.minutes()).isZero();
        assertThat(result.hourlyRates()).containsOnly(0);
    }

    @Test void 초미만을_보존해_합한후_분은_하루_합계에서_버린다() {
        var result = CompletedTripTime.calculate(List.of(new Interval(1, day, day.plusSeconds(30).plusNanos(500_000_000)),
                new Interval(2, day, day.plusSeconds(30).plusNanos(500_000_000))), day, 2);
        assertThat(result.minutes()).isEqualTo(1);
        assertThat(result.hourlyRates()[0]).isCloseTo(61.0 / 7200 * 100, within(1e-9));
    }

    @Test void 차량0은_0이고_분모보다_많은_차량의_구간은_거부한다() {
        assertThat(CompletedTripTime.calculate(List.of(), day, 0).minutes()).isZero();
        assertThatThrownBy(() -> CompletedTripTime.calculate(List.of(trip(1, 0, 24)), day, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Interval trip(long vehicle, int from, int to) {
        return new Interval(vehicle, day.plusHours(from), day.plusHours(to));
    }
}
