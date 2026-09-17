package org.thisway.vehicle.log.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.emulator.credential.DeviceBindingGuard;
import org.thisway.emulator.credential.DeviceIdentity;
import org.thisway.vehicle.domain.VehicleReference;
import org.thisway.vehicle.log.interfaces.*;
import org.thisway.vehicle.triplog.application.StreamCoordinatesService;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class DeviceTelemetryService {
    private final DeviceBindingGuard bindingGuard;
    private final LogService logService;
    private final StreamCoordinatesService streams;

    public void savePowerLog(PowerLogRequest request, DeviceIdentity identity) {
        PowerLogRequestValidator.validate(request);
        bindingGuard.requireCurrent(identity, request.mdn());
        // The exact MDN and its owning rows stay locked through the existing core write.
        logService.savePowerLog(request);
    }

    public void saveGeofenceLog(GeofenceLogRequest request, DeviceIdentity identity) {
        GeofenceLogRequestValidator.validate(request);
        bindingGuard.requireCurrent(identity, request.mdn());
        logService.saveGeofenceLog(request);
    }

    public void streamGps(GpsLogRequest request, DeviceIdentity identity) {
        GpsLogRequestValidator.validate(request);
        bindingGuard.requireCurrent(identity, request.mdn());
        // Match storage semantics for packets that omit per-entry minute/second.
        String minute = request.oTime().substring(10, 12);
        String second = request.oTime().length() == 14 ? request.oTime().substring(12, 14) : "00";
        var entries = request.cList().stream().map(entry -> new GpsLogEntry(
                entry.min() == null || entry.min().isEmpty() ? minute : entry.min(),
                entry.sec() == null || entry.sec().isEmpty() ? second : entry.sec(), entry.gcd(),
                entry.lat(), entry.lon(), entry.ang(), entry.spd(), entry.sum(), entry.bat())).toList();
        streams.sendCurrentCoordinates(new VehicleReference(identity.vehicleId(), identity.companyId()), entries);
    }
}
