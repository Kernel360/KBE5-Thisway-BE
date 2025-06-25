package org.thisway.triplog.service;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.thisway.log.repository.LogRepository;
import org.thisway.triplog.converter.ReverseGeocodingConverter;
import org.thisway.triplog.repository.TripLogRepository;
import org.thisway.vehicle.service.VehicleService;

@SpringBootTest
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class TripLogServiceTest {
    private final TripLogServiceImpl tripLogService;
    private final TripLogRepository tripLogRepository;

    @MockitoSpyBean
    private final VehicleService vehicleService;
    @MockitoBean
    private final LogRepository logRepository;
    @MockitoBean
    private final ReverseGeocodingConverter reverseGeocodingConverter;

    @Disabled
    @Test
    @DisplayName("차량 상세 페이지 내 필요한 정보들에 대해 조회 성공한다.")
    void 차량_상세_페이지_조회_성공() {

    }

    @Disabled
    @Test
    @DisplayName("운행 중인 차량 실시간 운행 정보 요청 시 조회 성공한다.")
    void 운행중_차량_실시간_운행_정보_조회_성공() {

    }

    @Disabled
    @Test
    @DisplayName("미운행 중인 차량 실시간 운행 정보 요청 시 VEHICLE_POWER_OFF 에러 발생한다.")
    void 미운행중_차량_실시간_정보_요청시_POWER_OFF_에러() {

    }

    @Disabled
    @Test
    @DisplayName("운행 중인 차량 운행 기록 없을 시 TRIP_LOG_NOT_FOUND 에러 발생한다.")
    void 운행중_차량_운행기록_없을시_NOT_FOUND_에러() {

    }

    @Disabled
    @Test
    @DisplayName("업체에 맞는 운행 기록 목록 조회 성공한다.")
    void 운행기록_목록_조회_성공() {

    }

    @Disabled
    @Test
    @DisplayName("유효한 start_time, end_time 입력 시 운행기록 상세 정보 조회 성공한다.")
    void 운행기록_상세_정보_조회_성공() {

    }

    @Disabled
    @Test
    @DisplayName("유효하지 않은 start_time, end_time 입력 시 운행기록 상세 정보 조회 실패한다.")
    void 운행기록_상세_정보_조회_실패() {

    }

    @Disabled
    @Test
    @DisplayName("power_on 시 운행 기록 생성 성공한다.")
    void power_on_운행기록_생성_성공() {

    }

    @Disabled
    @Test
    @DisplayName("power_off 시, 운행 기록 테이블에 on 데이터가 없다면 운행 기록을 새로 생성한다.")
    void power_off_운행기록_생성_성공() {

    }

    @Disabled
    @Test
    @DisplayName("power_off 시, 운행 기록 테이블에서 on 데이터를 찾아 수정한다.")
    void power_off_운행기록_수정_성공() {

    }
}
