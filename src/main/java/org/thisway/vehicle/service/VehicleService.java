package org.thisway.vehicle.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final CompanyRepository companyRepository;
    private final VehicleDetailRepository vehicleDetailRepository;

    public void registerVehicle(VehicleCreateRequest request) {

        VehicleDetail vehicleDetail = request.toVehicleDetailEntity();

        VehicleDetail savedVehicleDetail = vehicleDetailRepository.save(vehicleDetail);

        //TODO: (하드코딩)업체로 로그인한 이후에 차량을 등록 -> 인가 시 업체 Id나 사업자등록번호를 받아서 유효성 검증 후 구현
        Company company = companyRepository.findById(1L)
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
}
