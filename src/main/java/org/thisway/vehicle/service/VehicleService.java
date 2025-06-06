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
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.security.service.SecurityService;
import org.thisway.vehicle.dto.request.VehicleCreateRequest;
import org.thisway.vehicle.dto.request.VehicleUpdateRequest;
import org.thisway.vehicle.dto.response.VehicleResponse;
import org.thisway.vehicle.dto.response.VehiclesResponse;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleDetail;
import org.thisway.vehicle.repository.VehicleDetailRepository;
import org.thisway.vehicle.repository.VehicleRepository;
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
    private final SecurityService securityService;

    private static final int MAX_PAGE_SIZE = 20;
    private static final List<String> ALLOWED_SORT_PROPERTIES = List.of(
            "id", "carNumber", "color", "mileage"
    );

    public void registerVehicle(VehicleCreateRequest request) {
        Member member = getCurrentMember();
        Company company = validateMemberCompanyAndPermission(member);
        VehicleDetail vehicleDetail = request.toVehicleDetailEntity();
        VehicleDetail savedVehicleDetail = vehicleDetailRepository.save(vehicleDetail);
        Vehicle vehicle = request.toVehicleEntity(company, savedVehicleDetail);
        vehicleRepository.save(vehicle);
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicleDetail(Long id) {
        Vehicle vehicle = getAuthorizedVehicle(id);
        return VehicleResponse.fromVehicle(vehicle);
    }

    public void deleteVehicle(Long id) {
        Vehicle vehicle = getAuthorizedVehicle(id);

        if (!vehicle.isActive()) {
            throw new CustomException(ErrorCode.VEHICLE_ALREADY_DELETED);
        }

        vehicle.delete();
    }

    @Transactional(readOnly = true)
    public VehiclesResponse getVehicles(Pageable pageable) {
        validatePageable(pageable);
        Member member = getCurrentMember();
        Company company = getMemberCompany(member);
        return VehiclesResponse.from(vehicleRepository.findAllByCompanyAndActiveTrue(company, pageable));
    }

    public void updateVehicle(Long id, VehicleUpdateRequest request) {
        Vehicle vehicle = getAuthorizedVehicle(id);
        vehicleUpdateValidator.validateUpdateRequest(vehicle, request);
        vehicle.update(request);
    }

    public void updateVehiclePowerState(Long id, boolean powerOn) {
        Vehicle vehicle = findActiveVehicle(id);

        if (vehicle.isPowerOn() != powerOn) {
            vehicle.updatePowerOn(powerOn);
        }
    }

    private Vehicle findActiveVehicle(Long id) {
        return vehicleRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new CustomException(ErrorCode.VEHICLE_NOT_FOUND));
    }

    private void validatePageable(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.PAGE_INVALID_PAGE_SIZE);
        }

        pageable.getSort().forEach(order -> {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new CustomException(ErrorCode.PAGE_INVALID_SORT_PROPERTY);
            }
        });
    }

    private Member getCurrentMember() {
        return securityService.getCurrentMember();
    }

    private Company getMemberCompany(Member member) {
        Company memberCompany = member.getCompany();
        if (memberCompany == null) {
            throw new CustomException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return memberCompany;
    }

    private Company validateMemberCompanyAndPermission(Member member) {
        Company memberCompany = getMemberCompany(member);
        validateCompanyAdminPermission(member);

        return companyRepository.findById(memberCompany.getId())
                .filter(BaseEntity::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
    }

    private void validateCompanyAdminPermission(Member member) {
        if (!member.getRole().getLowerOrEqualRoles().contains(MemberRole.COMPANY_ADMIN)) {
            throw new CustomException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

    private Vehicle getAuthorizedVehicle(Long id) {
        Member member = getCurrentMember();
        Company memberCompany = getMemberCompany(member);
        validateCompanyAdminPermission(member);
        Vehicle vehicle = findActiveVehicle(id);
        validateVehicleCompanyMatch(vehicle, memberCompany);

        return vehicle;
    }

    private void validateVehicleCompanyMatch(Vehicle vehicle, Company memberCompany) {
        Company vehicleCompany = vehicle.getCompany();
        if (vehicleCompany == null || !vehicleCompany.getId().equals(memberCompany.getId())) {
            throw new CustomException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }
}
