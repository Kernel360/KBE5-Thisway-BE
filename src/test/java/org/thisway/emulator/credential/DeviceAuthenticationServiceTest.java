package org.thisway.emulator.credential;

import org.junit.jupiter.api.Test;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeviceAuthenticationServiceTest {
    private final DeviceCredentialRepository repository = mock(DeviceCredentialRepository.class);
    private final DeviceAuthenticationService service = new DeviceAuthenticationService(repository);

    @Test
    void 누락_과대_비ASCII_키와_유효하지않은_식별자는_DB조회전에_거부한다() {
        String key = DeviceKeyMaterial.generate();
        for (String invalid : Arrays.asList(null, "", "Bearer " + key, key + " ", "x".repeat(100_000),
                "twdev_" + "가".repeat(43), "twdev_" + "!".repeat(43))) {
            rejected(() -> service.authenticate(1, invalid, "mdn"));
        }
        for (String invalidMdn : Arrays.asList(null, "", " ", "x".repeat(21))) {
            rejected(() -> service.authenticate(1, key, invalidMdn));
        }
        rejected(() -> service.authenticate(0, key, "mdn"));
        rejected(() -> service.authenticate(-1, key, "mdn"));
        verifyNoInteractions(repository);
    }

    @Test
    void 미등록과_틀린키는_같은_인증오류를_반환한다() {
        String key = DeviceKeyMaterial.generate();
        when(repository.findAuthenticationCandidate(eq(1L), any())).thenReturn(Optional.empty());
        rejected(() -> service.authenticate(1, key, "mdn"));
        when(repository.findAuthenticationCandidate(eq(1L), any())).thenReturn(Optional.of(
                new DeviceCredentialRepository.AuthenticationCandidate(new DeviceIdentity(1, 2, 3, "mdn", 4),
                        DeviceKeyMaterial.hash(DeviceKeyMaterial.generate()))));
        rejected(() -> service.authenticate(1, key, "mdn"));
    }

    @Test
    void DB장애는_잘못된키로_위장하지않고_실패를_전파한다() {
        var failure = new org.springframework.dao.DataAccessResourceFailureException("fixture unavailable");
        when(repository.findAuthenticationCandidate(eq(1L), any())).thenThrow(failure);
        assertThatThrownBy(() -> service.authenticate(1, DeviceKeyMaterial.generate(), "mdn")).isSameAs(failure);
    }

    private void rejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DEVICE_AUTHENTICATION_FAILED))
                .hasMessage(ErrorCode.DEVICE_AUTHENTICATION_FAILED.getMessage());
    }
}
