package org.thisway.log.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.common.ApiResponse;
import org.thisway.log.LogDataSaveService;
import org.thisway.log.dto.request.LogDataBatchRequest;

@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogDataSaveService logDataSaveService;

    @PostMapping("/batch")
    public ApiResponse<Void> receiveLogDataBatch(@RequestBody LogDataBatchRequest request) {
        log.info("로그 데이터 배치 수신: 차량 ID={}, MDN={}, 항목 수={}", 
                request.vehicleId(), request.mdn(), request.entries().size());
        
        logDataSaveService.saveBatchLogData(request);
        
        return ApiResponse.ok();
    }
}
