package org.thisway.vehicle.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.security.service.SecurityService;
import org.thisway.vehicle.dto.request.VehicleModelCreateRequest;
import org.thisway.vehicle.dto.response.VehicleModelsResponse;
import org.thisway.vehicle.entity.VehicleModel;
import org.thisway.vehicle.repository.VehicleModelRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleModelService {

  private final VehicleModelRepository vehicleModelRepository;
  private final SecurityService securityService;

  private final static int MAX_PAGE_SIZE = 20;
  private final List<String> ALLOWED_SORT_PROPERTIES = List.of(
      "id", "manufacturer", "model", "modelYear"
  );

  public void registerVehicleModel(VehicleModelCreateRequest request) {

    Member member = getCurrentMember();
    validatePermission(member);
    validateDuplicateModel(request);
    VehicleModel vehicleModel = request.toEntity();
    vehicleModelRepository.save(vehicleModel);
  }

  public VehicleModelsResponse getVehicleModels(Pageable pageable){
    validatePageable(pageable);
    Member member = getCurrentMember();
    Page<VehicleModel> vehicleModels = vehicleModelRepository.findAllByActiveTrue(pageable);
    return VehicleModelsResponse.from(vehicleModels);
  }


  private void validatePageable(Pageable pageable) {
    if(pageable.getPageSize() > MAX_PAGE_SIZE){
      throw new CustomException(ErrorCode.PAGE_INVALID_PAGE_SIZE);
    }

    pageable.getSort().forEach(order -> {
      if(!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())){
        throw new CustomException(ErrorCode.PAGE_INVALID_SORT_PROPERTY);
      }
    });
  }

  /**
   * 차량 모델 중복 검증
   */
  private void validateDuplicateModel(VehicleModelCreateRequest request) {
    boolean exists = vehicleModelRepository.existsByManufacturerAndNameAndModelYear(
        request.manufacturer(), request.name(), request.modelYear());

    if (exists) {
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
      throw new CustomException(ErrorCode.AUTH_UNAUTHORIZED);
    }
  }
}
