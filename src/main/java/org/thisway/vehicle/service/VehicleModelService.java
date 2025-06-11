package org.thisway.vehicle.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.security.service.SecurityService;
import org.thisway.vehicle.dto.request.VehicleModelCreateRequest;
import org.thisway.vehicle.entity.VehicleModel;
import org.thisway.vehicle.repository.VehicleModelRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VehicleModelService {

  private final VehicleModelRepository vehicleModelRepository;
  private final SecurityService securityService;

  public void registerVehicleModel(VehicleModelCreateRequest request) {
    log.info("차량 모델 등록 시작 - 제조사: {}, 모델: {}, 연도: {}",
        request.manufacturer(), request.model(), request.modelYear());

    Member member = getCurrentMember();
    validatePermission(member);
    validateDuplicateModel(request);
    VehicleModel vehicleModel = request.toEntity();
    vehicleModelRepository.save(vehicleModel);

    log.info("차량 모델 등록 완료 - ID: {}", vehicleModel.getId());
  }

  /**
   * 차량 모델 중복 검증
   */
  private void validateDuplicateModel(VehicleModelCreateRequest request) {
    boolean exists = vehicleModelRepository.existsByManufacturerAndModelAndModelYear(
        request.manufacturer(), request.model(), request.modelYear());

    if (exists) {
      log.warn("중복된 차량 모델 등록 시도 - 제조사: {}, 모델: {}, 연도: {}",
          request.manufacturer(), request.model(), request.modelYear());
      throw new CustomException(ErrorCode.VEHICLE_MODEL_ALREADY_EXISTS);
    }
  }

  /**
   * 현재 로그인한 사용자 정보 조회
   */
  private Member getCurrentMember() {
    return securityService.getCurrentMember();
  }

  /**
   * 차량 모델 등록 권한 검증
   * COMPANY_ADMIN 이상의 권한만 접근 가능하게 끔
   */
  private void validatePermission(Member member) {
    if (member.getRole().getLevel() < MemberRole.COMPANY_ADMIN.getLevel()) {
      log.warn("차량 모델 등록 권한 없음 - 사용자: {}, 역할: {}",
          member.getId(), member.getRole());
      throw new CustomException(ErrorCode.AUTH_UNAUTHORIZED);
    }
  }
}
