package org.thisway.log.dto.request;

import java.util.List;

public record LogDataBatchRequest(
        Long vehicleId,
        Long mdn,
        List<LogDataEntry> entries
) {}
