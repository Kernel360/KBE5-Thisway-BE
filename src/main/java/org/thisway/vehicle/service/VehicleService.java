package org.thisway.vehicle.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.BaseEntity;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.vehicle.dto.request.VehicleCreateRequest;
import org.thisway.vehicle.dto.response.VehicleResponse;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleDetail;
import org.thisway.vehicle.repository.VehicleDetailRepository;
import org.thisway.vehicle.repository.VehicleRepository;
import org.thisway.vehicle.dto.response.VehiclesResponse;
import org.thisway.vehicle.dto.request.VehicleUpdateRequest;
import org.thisway.vehicle.validation.VehicleUpdateValidator;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final CompanyRepository companyRepository;
    private final VehicleDetailRepository vehicleDetailRepository;
    private final VehicleUpdateValidator vehicleUpdateValidator;

    private static final int MAX_PAGE_SIZE = 20;
    private static final List<String> ALLOWED_SORT_PROPERTIES = List.of(
            "id", "carNumber", "color", "mileage"
    );

    public void registerVehicle(VehicleCreateRequest request) {

        VehicleDetail vehicleDetail = request.toVehicleDetailEntity();

        VehicleDetail savedVehicleDetail = vehicleDetailRepository.save(vehicleDetail);

        //TODO: (하드코딩)업체로 로그인한 이후에 차량을 등록 -> 인가 시 업체 Id나 사업자등록번호를 받아서 유효성 검증 후 구현
        Company company = companyRepository.findById(1L)
                .filter(BaseEntity::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        Vehicle vehicle = request.toVehicleEntity(company, savedVehicleDetail);
        vehicleRepository.save(vehicle);
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicleDetail(Long id) {

        //TODO : 업체로 로그인 했을시 권한 확인 로직 추가
        Vehicle vehicle = vehicleRepository.findByIdAndActiveTrue(id)
                .orElseThrow(()-> new CustomException(ErrorCode.VEHICLE_NOT_FOUND));

        return VehicleResponse.fromVehicle(vehicle);
    }

    public void deleteVehicle(Long id) {

        //TODO: 권한 검증 추가 예정(인증된 사용자만 삭제할 권한이 있도록)
        //TODO: 삭제 이력은 남겨야할 것 같음. 누가, 언제 삭제했는지.
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.VEHICLE_NOT_FOUND));

        if (!vehicle.isActive()) {
            throw new CustomException(ErrorCode.VEHICLE_ALREADY_DELETED);
        }

        vehicle.delete();
    }

    @Transactional(readOnly = true)
    public VehiclesResponse getVehicles(Pageable pageable) {
        validatePageable(pageable);
        return VehiclesResponse.from(vehicleRepository.findAllByActiveTrue(pageable));
    }

    //TODO : 인증/인가(권한) 적용
    //TODO : 수정 이력 추가
    public void updateVehicle(Long id, VehicleUpdateRequest request) {
        Vehicle vehicle = findActiveVehicle(id);
        vehicleUpdateValidator.validateUpdateRequest(vehicle, request);
        vehicle.update(request);
    }

    private Vehicle findActiveVehicle(Long id) {
        return vehicleRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new CustomException(ErrorCode.VEHICLE_NOT_FOUND));
    }

    private void validatePageable(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_PAGE_SIZE);
        }

        //TODO : 현재는 VehicleDetail에 있는 속성으로는 정렬 불가. 추가예정.
        pageable.getSort().forEach(order -> {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new CustomException(ErrorCode.INVALID_SORT_PROPERTY);
            }
        });
    }
}
