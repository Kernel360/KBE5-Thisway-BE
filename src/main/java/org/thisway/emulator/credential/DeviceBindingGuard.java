package org.thisway.emulator.credential;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

@Service
@RequiredArgsConstructor
public class DeviceBindingGuard {
    private final DeviceCredentialRepository repository;

    // The caller must keep these row locks until its write/dispatch transaction completes.
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireCurrent(DeviceIdentity identity, String payloadMdn) {
        if (identity == null || identity.mdn() == null || !identity.mdn().equals(payloadMdn)) throw rejected();
        var current = repository.findOwned(identity.emulatorId(), identity.companyId(), true)
                .orElseThrow(DeviceBindingGuard::rejected);
        if (!current.active() || current.vehicleId() != identity.vehicleId()
                || current.assignmentRevision() != identity.assignmentRevision()
                || !current.mdn().equals(identity.mdn())) throw rejected();
    }

    private static CustomException rejected() {
        return new CustomException(ErrorCode.DEVICE_AUTHENTICATION_FAILED);
    }
}
