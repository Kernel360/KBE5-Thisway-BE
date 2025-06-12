package org.thisway.member.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VerificationPayload(@NotBlank String code, @NotBlank long expiryTime) {

    public Boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }
}
