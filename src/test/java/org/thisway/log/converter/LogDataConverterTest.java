package org.thisway.log.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LogDataConverterTest {

    @InjectMocks
    private LogDataConverter converter;

    @Test
    @DisplayName("좌표 변환 테스트")
    void 좌표_파싱이_올바르게_실행되어야한다() {
        String coordinate = "4140338";
        Double expected = 4.140338;

        Double result = converter.convertCoordinate(coordinate);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("날짜 시간 변환 테스트 (초 미포함)")
    void 날짜_시간_파싱이_올바르게_실행되어야한다() {
        String dateTime = "2109010920";
        LocalDateTime expected = LocalDateTime.of(2021, 9, 1, 9, 20);

        LocalDateTime result = converter.convertDateTime(dateTime);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("날짜 시간 변환 테스트 (초 포함)")
    void 날짜_시간_초_파싱이_올바르게_실행되어야한다() {
        String dateTime = "210901092033";
        LocalDateTime expected = LocalDateTime.of(2021, 9, 1, 9, 20, 33);

        LocalDateTime result = converter.convertDateTimeWithSec(dateTime);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("문자열을 Integer로 변환 테스트")
    void Integer_파싱이_올바르게_실행되어야한다() {
        String value = "123";
        Integer expected = 123;

        Integer result = converter.convertToInteger(value);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("문자열을 Long으로 변환 테스트")
    void Long_파싱이_올바르게_실행되어야한다() {
        String value = "123";
        Long expected = 123L;

        Long result = converter.convertToLong(value);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("문자열을 Byte로 변환 테스트")
    void Byte_파싱이_올바르게_실행되어야한다() {
        String value = "123";
        Byte expected = (byte) 123;

        Byte result = converter.convertToByte(value);
        assertThat(result).isEqualTo(expected);
    }
}
