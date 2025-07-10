package org.thisway.vehicle.interfaces;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.security.dto.request.MemberDetails;
import org.thisway.security.utils.JwtTokenUtil;
import org.thisway.vehicle.triplog.application.StreamCoordinatesService;
import org.thisway.vehicle.application.VehicleService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final StreamCoordinatesService streamCoordinatesService;
    private final JwtTokenUtil jwtTokenUtil;

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
        if (!jwtTokenUtil.isValid(token)) {
            throw new CustomException(ErrorCode.AUTH_UNAUTHENTICATED);
        }
        Claims claims = jwtTokenUtil.validateTokenAndGetClaims(token);
        return streamCoordinatesService.createStreamForCompany(claims.get("companyId", Long.class), claims.getSubject());
    }
}
