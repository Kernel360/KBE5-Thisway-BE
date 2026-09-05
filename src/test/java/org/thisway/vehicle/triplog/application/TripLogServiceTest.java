package org.thisway.vehicle.triplog.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.support.security.dto.request.MemberDetails;
import org.thisway.support.security.service.SecurityService;
import org.thisway.vehicle.application.VehicleService;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.interfaces.VehicleResponse;
import org.thisway.vehicle.log.application.LogService;
import org.thisway.vehicle.log.domain.GpsLogData;
import org.thisway.vehicle.log.domain.GpsStatus;
import org.thisway.vehicle.triplog.domain.ReverseGeocodeResult;
import org.thisway.vehicle.triplog.domain.ReverseGeocodingConverter;
import org.thisway.vehicle.triplog.domain.TripLog;
import org.thisway.vehicle.triplog.domain.TripLogSaveInput;
import org.thisway.vehicle.triplog.infrastructure.TripLogRepository;
import org.thisway.vehicle.triplog.interfaces.CurrentTripLogResponse;
import org.thisway.vehicle.triplog.interfaces.TripLogDetailResponse;
import org.thisway.vehicle.triplog.interfaces.TripLogsResponse;
import org.thisway.vehicle.triplog.interfaces.VehicleDetailResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripLogServiceTest {

    private static final Long VEHICLE_ID = 1L;
    private static final Long COMPANY_ID = 10L;
    private static final LocalDateTime ON_TIME = LocalDateTime.of(2026, 9, 5, 9, 0);
    private static final LocalDateTime OFF_TIME = LocalDateTime.of(2026, 9, 5, 10, 0);
    private static final Double LATITUDE = 37.5665;
    private static final Double LONGITUDE = 126.9780;
    private static final ReverseGeocodeResult ADDRESS = new ReverseGeocodeResult("서울특별시 중구", "세종대로 110");

    @Mock
    private VehicleService vehicleService;
    @Mock
    private LogService logService;
    @Mock
    private TripLogRepository tripLogRepository;
    @Mock
    private ReverseGeocodingConverter reverseGeocodingConverter;
    @Mock
    private SecurityService securityService;
    @Mock
    private Vehicle vehicle;

    @InjectMocks
    private TripLogServiceImpl tripLogService;

    @Test
    @DisplayName("운행 중 차량 상세 조회 시 현재 운행과 완료 운행을 분리한다")
    void 운행중_차량_상세_조회시_현재운행과_완료운행을_분리한다() {
        VehicleResponse vehicleResponse = vehicleResponse(true);
        TripLog currentTrip = tripLog(ON_TIME, null, 1_000, false);
        TripLog completedTrip = tripLog(ON_TIME.minusDays(1), OFF_TIME.minusDays(1), 300, true);
        GpsLogData latestGps = gpsLog(ON_TIME.plusMinutes(10), 1_120, 45);

        when(vehicleService.getVehicleDetail(VEHICLE_ID)).thenReturn(vehicleResponse);
        when(securityService.getCurrentMemberDetails()).thenReturn(memberDetails());
        when(tripLogRepository.findTop6ByVehicleIdAndVehicleCompanyIdOrderByStartTimeDesc(
                VEHICLE_ID, COMPANY_ID))
                .thenReturn(new ArrayList<>(List.of(currentTrip, completedTrip)));
        when(logService.getCurrentGpsLog(VEHICLE_ID, ON_TIME)).thenReturn(latestGps);
        when(vehicle.getId()).thenReturn(VEHICLE_ID);
        when(vehicle.getCarNumber()).thenReturn("12가3456");

        VehicleDetailResponse response = tripLogService.getVehicleDetails(VEHICLE_ID);

        assertThat(response.vehicleResponse()).isEqualTo(vehicleResponse);
        assertThat(response.currentDrivingInfo()).satisfies(current -> {
            assertThat(current.startTime()).isEqualTo(ON_TIME);
            assertThat(current.tripMeter()).isEqualTo(120);
            assertThat(current.speed()).isEqualTo(45);
            assertThat(current.latitude()).isEqualTo(LATITUDE);
            assertThat(current.longitude()).isEqualTo(LONGITUDE);
        });
        assertThat(response.tripLogBriefInfos()).singleElement().satisfies(completed -> {
            assertThat(completed.vehicleId()).isEqualTo(VEHICLE_ID);
            assertThat(completed.tripMeter()).isEqualTo(300);
        });
    }

    @Test
    @DisplayName("시동 ON 차량의 GPS 로그가 있으면 마지막 로그와 이동 경로를 반환한다")
    void 시동ON_차량의_GPS로그가_있으면_실시간정보를_반환한다() {
        GpsLogData first = gpsLog(ON_TIME.plusMinutes(1), 1_010, 30);
        GpsLogData last = gpsLog(ON_TIME.plusMinutes(2), 1_025, 40);
        when(vehicleService.getVehicleDetail(VEHICLE_ID)).thenReturn(vehicleResponse(true));
        when(logService.findGpsLogs(eq(VEHICLE_ID), eq(ON_TIME), any(LocalDateTime.class)))
                .thenReturn(List.of(first, last));

        CurrentTripLogResponse response = tripLogService.getCurrentGpsLogs(VEHICLE_ID, ON_TIME);

        assertThat(response.angle()).isEqualTo(90);
        assertThat(response.speed()).isEqualTo(40);
        assertThat(response.totalTripMeter()).isEqualTo(1_025);
        assertThat(response.coordinatesInfo()).hasSize(2);
        assertThat(response.coordinatesInfo().getLast().vehicleId()).isEqualTo(VEHICLE_ID);
    }

    @Test
    @DisplayName("시동 OFF 차량의 실시간 정보 요청은 VEHICLE_POWER_OFF를 발생시킨다")
    void 시동OFF_차량의_실시간정보_요청은_실패한다() {
        when(vehicleService.getVehicleDetail(VEHICLE_ID)).thenReturn(vehicleResponse(false));

        assertThatThrownBy(() -> tripLogService.getCurrentGpsLogs(VEHICLE_ID, ON_TIME))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VEHICLE_POWER_OFF);
        verifyNoInteractions(logService);
    }

    @Test
    @DisplayName("시동 ON 차량이라도 조회 구간의 GPS 로그가 없으면 null을 반환한다")
    void 시동ON_차량의_GPS로그가_없으면_null을_반환한다() {
        when(vehicleService.getVehicleDetail(VEHICLE_ID)).thenReturn(vehicleResponse(true));
        when(logService.findGpsLogs(eq(VEHICLE_ID), eq(ON_TIME), any(LocalDateTime.class)))
                .thenReturn(List.of());

        CurrentTripLogResponse response = tripLogService.getCurrentGpsLogs(VEHICLE_ID, ON_TIME);

        assertThat(response).isNull();
    }

    @Test
    @DisplayName("회사의 완료 운행 목록과 페이지 정보를 반환한다")
    void 회사의_완료운행_목록을_반환한다() {
        Pageable pageable = PageRequest.of(0, 10);
        TripLog completedTrip = tripLog(ON_TIME, OFF_TIME, 500, true);
        when(vehicle.getId()).thenReturn(VEHICLE_ID);
        when(vehicle.getCarNumber()).thenReturn("12가3456");
        when(tripLogRepository.findAllByCompanyAndActiveTrueOrderByStartTimeDesc(COMPANY_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(completedTrip), pageable, 1));

        TripLogsResponse response = tripLogService.findTripLogs(COMPANY_ID, pageable);

        assertThat(response.tripLogs()).singleElement().satisfies(trip -> {
            assertThat(trip.vehicleId()).isEqualTo(VEHICLE_ID);
            assertThat(trip.tripMeter()).isEqualTo(500);
        });
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.currentPage()).isZero();
        assertThat(response.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("운행 상세 조회 시 GPS 평균 속도와 출도착 주소를 조립한다")
    void 운행상세_조회시_GPS_평균속도와_주소를_반환한다() {
        Long tripId = 20L;
        TripLog completedTrip = TripLog.builder()
                .vehicle(vehicle)
                .startTime(ON_TIME)
                .endTime(OFF_TIME)
                .totalTripMeter(500)
                .onAddress("서울특별시 중구")
                .onAddrDetail("세종대로 110")
                .offAddress("서울특별시 강남구")
                .offAddrDetail("테헤란로 1")
                .active(true)
                .build();
        when(vehicle.getId()).thenReturn(VEHICLE_ID);
        when(vehicle.getCarNumber()).thenReturn("12가3456");
        when(securityService.getCurrentMemberDetails()).thenReturn(memberDetails());
        when(tripLogRepository.findByIdAndVehicleCompanyIdAndActiveTrue(tripId, COMPANY_ID))
                .thenReturn(Optional.of(completedTrip));
        when(logService.findGpsLogs(VEHICLE_ID, ON_TIME, OFF_TIME)).thenReturn(List.of(
                gpsLog(ON_TIME.plusMinutes(10), 1_200, 40),
                gpsLog(ON_TIME.plusMinutes(20), 1_300, 61)
        ));

        TripLogDetailResponse response = tripLogService.getTripLogDetails(tripId);

        assertThat(response.carNumber()).isEqualTo("12가3456");
        assertThat(response.avgSpeed()).isEqualTo(50.5);
        assertThat(response.onAddress()).isEqualTo("서울특별시 중구세종대로 110");
        assertThat(response.offAddress()).isEqualTo("서울특별시 강남구테헤란로 1");
    }

    @Test
    @DisplayName("존재하지 않는 운행 상세 조회는 TRIP_LOG_NOT_FOUND를 발생시킨다")
    void 존재하지_않는_운행상세_조회는_실패한다() {
        Long tripId = 404L;
        when(securityService.getCurrentMemberDetails()).thenReturn(memberDetails());
        when(tripLogRepository.findByIdAndVehicleCompanyIdAndActiveTrue(tripId, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripLogService.getTripLogDetails(tripId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRIP_LOG_NOT_FOUND);
        verifyNoInteractions(logService);
    }

    @Test
    @DisplayName("시동 ON 이벤트는 미완료 운행을 생성한다")
    void 시동ON_이벤트는_미완료_운행을_생성한다() {
        TripLogSaveInput input = saveInput(null, 1_000);
        when(reverseGeocodingConverter.convertToAddress(LATITUDE, LONGITUDE)).thenReturn(ADDRESS);

        tripLogService.saveTripLog(input);

        TripLog saved = captureSavedTripLog();
        assertThat(saved.getVehicle()).isSameAs(vehicle);
        assertThat(saved.getStartTime()).isEqualTo(ON_TIME);
        assertThat(saved.getEndTime()).isNull();
        assertThat(saved.getTotalTripMeter()).isEqualTo(1_000);
        assertThat(saved.getOnLatitude()).isEqualTo(LATITUDE);
        assertThat(saved.getOnLongitude()).isEqualTo(LONGITUDE);
        assertThat(saved.getOnAddr()).isEqualTo(ADDRESS.addr());
        assertThat(saved.getOnAddrDetail()).isEqualTo(ADDRESS.addrDetail());
        assertThat(saved.isActive()).isFalse();
        verify(tripLogRepository, never()).findByVehicleIdAndStartTime(VEHICLE_ID, ON_TIME);
    }

    @Test
    @DisplayName("선행 ON 없이 OFF 이벤트가 오면 거리 0의 완료 운행을 생성한다")
    void 선행ON_없는_OFF_이벤트는_거리0의_완료운행을_생성한다() {
        TripLogSaveInput input = saveInput(OFF_TIME, 1_500);
        when(vehicle.getId()).thenReturn(VEHICLE_ID);
        when(reverseGeocodingConverter.convertToAddress(LATITUDE, LONGITUDE)).thenReturn(ADDRESS);
        when(tripLogRepository.findByVehicleIdAndStartTime(VEHICLE_ID, ON_TIME)).thenReturn(null);

        tripLogService.saveTripLog(input);

        TripLog saved = captureSavedTripLog();
        assertThat(saved.getStartTime()).isEqualTo(ON_TIME);
        assertThat(saved.getEndTime()).isEqualTo(OFF_TIME);
        assertThat(saved.getTotalTripMeter()).isZero();
        assertThat(saved.getOnLatitude()).isNull();
        assertThat(saved.getOffLatitude()).isEqualTo(LATITUDE);
        assertThat(saved.getOffLongitude()).isEqualTo(LONGITUDE);
        assertThat(saved.getOffAddr()).isEqualTo(ADDRESS.addr());
        assertThat(saved.getOffAddrDetail()).isEqualTo(ADDRESS.addrDetail());
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @DisplayName("선행 ON이 있는 OFF 이벤트는 같은 운행을 완료 상태로 변경한다")
    void 선행ON_있는_OFF_이벤트는_기존운행을_완료한다() {
        TripLog existingTrip = tripLog(ON_TIME, null, 1_000, false);
        TripLogSaveInput input = saveInput(OFF_TIME, 1_500);
        when(vehicle.getId()).thenReturn(VEHICLE_ID);
        when(reverseGeocodingConverter.convertToAddress(LATITUDE, LONGITUDE)).thenReturn(ADDRESS);
        when(tripLogRepository.findByVehicleIdAndStartTime(VEHICLE_ID, ON_TIME)).thenReturn(existingTrip);

        tripLogService.saveTripLog(input);

        verify(tripLogRepository).save(same(existingTrip));
        assertThat(existingTrip.getEndTime()).isEqualTo(OFF_TIME);
        assertThat(existingTrip.getTotalTripMeter()).isEqualTo(1_500);
        assertThat(existingTrip.getOffLatitude()).isEqualTo(LATITUDE);
        assertThat(existingTrip.getOffLongitude()).isEqualTo(LONGITUDE);
        assertThat(existingTrip.getOffAddr()).isEqualTo(ADDRESS.addr());
        assertThat(existingTrip.getOffAddrDetail()).isEqualTo(ADDRESS.addrDetail());
        assertThat(existingTrip.isActive()).isTrue();
    }

    private VehicleResponse vehicleResponse(boolean powerOn) {
        return new VehicleResponse(
                VEHICLE_ID,
                "현대",
                2025,
                "아이오닉 5",
                "12가3456",
                "흰색",
                10_000,
                powerOn,
                LATITUDE,
                LONGITUDE
        );
    }

    private MemberDetails memberDetails() {
        return MemberDetails.builder()
                .companyId(COMPANY_ID)
                .build();
    }

    private TripLog tripLog(LocalDateTime startTime, LocalDateTime endTime, int totalTripMeter, boolean active) {
        return TripLog.builder()
                .vehicle(vehicle)
                .startTime(startTime)
                .endTime(endTime)
                .totalTripMeter(totalTripMeter)
                .onLatitude(LATITUDE)
                .onLongitude(LONGITUDE)
                .onAddress(ADDRESS.addr())
                .onAddrDetail(ADDRESS.addrDetail())
                .active(active)
                .build();
    }

    private GpsLogData gpsLog(LocalDateTime occurredTime, int totalTripMeter, int speed) {
        return new GpsLogData(
                VEHICLE_ID,
                "01234567890",
                GpsStatus.NORMAL,
                LATITUDE,
                LONGITUDE,
                90,
                speed,
                totalTripMeter,
                14,
                occurredTime
        );
    }

    private TripLogSaveInput saveInput(LocalDateTime offTime, int totalTripMeter) {
        return new TripLogSaveInput(
                vehicle,
                "01234567890",
                ON_TIME,
                offTime,
                LATITUDE,
                LONGITUDE,
                totalTripMeter
        );
    }

    private TripLog captureSavedTripLog() {
        ArgumentCaptor<TripLog> captor = ArgumentCaptor.forClass(TripLog.class);
        verify(tripLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
