package org.thisway.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VerificationEntry(@NotBlank String code, @NotBlank long expiryTime) {
}
