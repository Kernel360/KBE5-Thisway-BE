package org.thisway.vehicle.log.interfaces;

public record LogResponse(
        String rstCd,
        String rstMsg,
        String mdn
) {
}
