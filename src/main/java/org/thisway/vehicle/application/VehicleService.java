package org.thisway.vehicle.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.support.common.BaseEntity;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.company.domain.Company;
import org.thisway.company.intrastructure.CompanyRepository;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.security.service.SecurityService;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.interfaces.*;
import org.thisway.vehicle.domain.VehicleTrackClient;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;
import org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository;
import org.thisway.vehicle.infrastructure.VehicleRepository;
import org.thisway.vehicle.interfaces.VehicleTrackResponse;
import org.thisway.vehicle.interfaces.VehicleTracksResponse;
import org.thisway.vehicle.util.VehicleUpdateValidator;

import java.util.List;

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
    private final VehicleTrackClient vehicleTrackClient;

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
    public VehiclesResponse getVehicles(VehicleSearchRequest searchRequest, Pageable pageable) {
        validatePageable(pageable);
        Member member = getCurrentMember();
        Company company = getMemberCompany(member);
        return VehiclesResponse.from(vehicleRepository.searchActiveVehicles(company, searchRequest, pageable));
    }

    public void saveVehicle(Vehicle vehicle) {
        vehicleRepository.save(vehicle);
    }

    public void updateVehicle(Long id, VehicleUpdateRequest request) {
        Vehicle vehicle = getAuthorizedVehicle(id);
        vehicleUpdateValidator.validateUpdateRequest(vehicle, request);

        VehicleModel vehicleModel = null;
        if (request.vehicleModelId() != null) {
            vehicleModel = findActiveVehicleModel(request.vehicleModelId());
        }

        vehicle.update(request, vehicleModel);
    }

    @Transactional(readOnly = true)
    public Vehicle getVehicleById(Long id) {
        return vehicleRepository.findById(id).orElseThrow(
                () -> new CustomException(ErrorCode.VEHICLE_NOT_FOUND)
        );
    }

    @Transactional(readOnly = true)
    public Boolean getVehiclePowerState(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id).orElseThrow(
                () -> new CustomException(ErrorCode.VEHICLE_NOT_FOUND)
        );
        return vehicle.isPowerOn();
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

    public VehicleTracksResponse getVehicleTracks(long companyId, Pageable pageable) {
        return vehicleTrackClient.trackVehicles(companyId, pageable);
    }

    public List<VehicleTrackResponse> getVehicleTracks(long companyId) {
        return vehicleTrackClient.trackVehicles(companyId);
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
