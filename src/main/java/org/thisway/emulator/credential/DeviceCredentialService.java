package org.thisway.emulator.credential;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.support.security.service.SecurityService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class DeviceCredentialService {
    public static final int VALID_DAYS = 30;
    private final DeviceCredentialRepository repository;
    private final SecurityService security;

    public DeviceCredentialService(DeviceCredentialRepository repository, SecurityService security) {
        this.repository = repository;
        this.security = security;
    }

    public IssuedDeviceKey issue(long emulatorId) {
        Member actor = administrator();
        var binding = owned(emulatorId, actor, true);
        if (binding.mdn() == null || binding.mdn().isBlank() || binding.mdn().length() > 20) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expires = now.plus(VALID_DAYS, ChronoUnit.DAYS);
        String key = DeviceKeyMaterial.generate();
        repository.replace(binding, DeviceKeyMaterial.hash(key), now, expires);
        repository.audit(binding, actor.getId(), "ISSUED", now);
        return new IssuedDeviceKey(key, expires);
    }

    public void revoke(long emulatorId) {
        Member actor = administrator();
        var binding = owned(emulatorId, actor, true);
        Instant now = Instant.now();
        if (repository.revoke(emulatorId, now)) repository.audit(binding, actor.getId(), "REVOKED", now);
    }

    public record Status(String state, Instant issuedAt, Instant expiresAt) {}

    @Transactional(readOnly = true)
    public Status status(long emulatorId) {
        var binding = owned(emulatorId, administrator(), false);
        return repository.find(emulatorId).map(snapshot -> {
            String state = !snapshot.hasKey() ? "REVOKED"
                    : snapshot.vehicleId() != binding.vehicleId() || snapshot.companyId() != binding.companyId()
                    || !snapshot.mdn().equals(binding.mdn()) ? "BINDING_CHANGED"
                    : !snapshot.expiresAt().isAfter(Instant.now()) ? "EXPIRED" : "ACTIVE";
            return new Status(state, snapshot.issuedAt(), snapshot.expiresAt());
        }).orElseGet(() -> new Status("NOT_ISSUED", null, null));
    }

    private Member administrator() {
        // Recheck live membership and role rather than trusting an old JWT's role claim alone.
        Member member = security.getCurrentMember();
        if (member.getRole() != MemberRole.COMPANY_ADMIN || !member.getCompany().isActive()) {
            throw new CustomException(ErrorCode.DEVICE_CREDENTIAL_FORBIDDEN);
        }
        return member;
    }

    private DeviceCredentialRepository.Binding owned(long id, Member actor, boolean lock) {
        return repository.findOwned(id, actor.getCompany().getId(), lock)
                .orElseThrow(() -> new CustomException(ErrorCode.EMULATOR_NOT_FOUND));
    }
}
