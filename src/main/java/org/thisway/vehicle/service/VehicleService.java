package org.thisway.vehicle.service;

import java.util.List;
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
import org.thisway.vehicle.dto.response.VehicleDashboardResponse;
import org.thisway.vehicle.dto.response.VehicleResponse;
import org.thisway.vehicle.dto.response.VehiclesResponse;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleModel;
import org.thisway.vehicle.repository.VehicleModelRepository;
import org.thisway.vehicle.repository.VehicleRepository;
import org.thisway.vehicle.validation.VehicleUpdateValidator;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleService {

    private static final int MAX_PAGE_SIZE = 20;
    private static final List<String> ALLOWED_SORT_PROPERTIES = List.of(
            "id", "carNumber", "color", "mileage", "powerOn"
    );
    private final VehicleRepository vehicleRepository;
    private final CompanyRepository companyRepository;
    private final VehicleModelRepository vehicleModelRepository;
    private final VehicleUpdateValidator vehicleUpdateValidator;
    private final SecurityService securityService;

    public void registerVehicle(VehicleCreateRequest request) {
        Member member = getCurrentMember();
        Company company = validateMemberCompanyAndPermission(member);
        VehicleModel vehicleModel = findActiveVehicleModel(request.vehicleModelId());
        isCarNumberDuplicate(request.carNumber());
        Vehicle vehicle = request.toVehicleEntity(company, vehicleModel);
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

    @Transactional(readOnly = true)
    public VehicleDashboardResponse getVehicleDashboard() {
        long companyId = securityService.getCurrentMemberDetails().getCompanyId();
        long totalVehicles = vehicleRepository.countByCompanyIdAndActiveTrue(companyId);
        long powerOnVehicles = vehicleRepository.countByCompanyIdAndPowerOnIsAndActiveTrue(companyId, true);
        long powerOffVehicles = vehicleRepository.countByCompanyIdAndPowerOnIsAndActiveTrue(companyId, false);

        return new VehicleDashboardResponse(
                totalVehicles,
                powerOnVehicles,
                powerOffVehicles
        );
    }

    private Vehicle findActiveVehicle(Long id) {
        return vehicleRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new CustomException(ErrorCode.VEHICLE_NOT_FOUND));
    }

    private VehicleModel findActiveVehicleModel(Long id) {
        return vehicleModelRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new CustomException(ErrorCode.VEHICLE_MODEL_NOT_FOUND));
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
        validatePermission(member);

        return companyRepository.findById(memberCompany.getId())
                .filter(BaseEntity::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
    }

    private void validateCompanyAdminPermission(Member member) {
        if (!member.getRole().getLowerOrEqualRoles().contains(MemberRole.COMPANY_ADMIN)) {
            throw new CustomException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

    private void validatePermission(Member member) {
        if (member.getRole().getLevel() < MemberRole.COMPANY_ADMIN.getLevel()) {
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

    private void isCarNumberDuplicate(String carNumber) {
        if (vehicleRepository.existsByCarNumberAndActiveTrue(carNumber)) {
            throw new CustomException(ErrorCode.VEHICLE_DUPLICATE_CAR_NUMBER);
        }
    }
}
