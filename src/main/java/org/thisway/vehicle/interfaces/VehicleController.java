package org.thisway.vehicle.interfaces;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.support.security.dto.request.MemberDetails;
import org.thisway.support.security.utils.JwtTokenProvider;
import org.thisway.vehicle.application.VehicleService;
import org.thisway.vehicle.triplog.application.StreamCoordinatesService;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final StreamCoordinatesService streamCoordinatesService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    public ResponseEntity<Void> registerVehicle(@RequestBody @Validated VehicleCreateRequest request) {

        vehicleService.registerVehicle(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> getVehicleDetail(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(vehicleService.getVehicleDetail(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .build();
    }

    @GetMapping
    public ResponseEntity<VehiclesResponse> getVehicles(
            @PageableDefault Pageable pageable,
            @ModelAttribute VehicleSearchRequest searchRequest
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(vehicleService.getVehicles(searchRequest, pageable));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateVehicle(@PathVariable Long id,
                                              @RequestBody @Validated VehicleUpdateRequest request) {
        vehicleService.updateVehicle(id, request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .build();
    }

    @GetMapping("/dashboard")
    public ResponseEntity<VehicleDashboardResponse> getVehicleDashboard() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(vehicleService.getVehicleDashboard());
    }

    @GetMapping("/track")
    public ResponseEntity<VehicleTracksResponse> getVehicleTracks(
            @PageableDefault Pageable pageable,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(vehicleService.getVehicleTracks(memberDetails.getCompanyId(), pageable));
    }

    @GetMapping("/stream/track")
    public SseEmitter getVehicleTracksStream(@RequestParam("token") String token) {
        Claims claims;
        try {
            claims = jwtTokenProvider.validateTokenAndGetClaims(token);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.AUTH_UNAUTHENTICATED);
        }
        return streamCoordinatesService.createStreamForCompany(claims.get("companyId", Long.class),
                claims.getSubject());
    }
}
