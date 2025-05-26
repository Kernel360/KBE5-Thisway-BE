package org.thisway.log;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogDataSchedulerService {

    private final LogDataGeneratorService logDataGeneratorService;

    @Scheduled(fixedRate = 5000) // 5초마다 실행
    public void scheduleLogGeneration() {
        logDataGeneratorService.generateAndSaveLogsForActiveVehicles();
    }
}
