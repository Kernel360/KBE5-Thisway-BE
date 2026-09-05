package org.thisway.company.statistics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.company.statistics.application.StatisticService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Configuration
@EnableBatchProcessing
@EnableScheduling
@RequiredArgsConstructor
public class StatisticBatchConfig {
    public static final String TARGET_DATE = "targetDate";
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final StatisticService statisticService;
    private final CompanyRepository companyRepository;
    private final JobLauncher jobLauncher;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job statisticsJob() {
        return new JobBuilder("statisticsJob", jobRepository)
                .validator(StatisticBatchConfig::validateParameters)
                .start(statisticsStep())
                .build();
    }

    @Bean
    public Step statisticsStep() {
        return new StepBuilder("statisticsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    LocalDate targetDate = LocalDate.parse((String) chunkContext.getStepContext()
                            .getJobParameters().get(TARGET_DATE));
                    List<Long> companyIds = companyRepository.findAllActiveCompanyIds();
                    for (Long companyId : companyIds) {
                        try {
                            statisticService.saveStatistics(companyId, targetDate);
                            log.info("회사 ID {}의 통계 처리 완료 (Step commit 대기)", companyId);
                        } catch (Exception e) {
                            // One transaction currently covers the whole tasklet. Do not swallow a failure.
                            throw new IllegalStateException("Statistics failed: companyId=" + companyId
                                    + ", targetDate=" + targetDate, e);
                        }
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Scheduled(cron = "${thisway.statistics.cron:0 0 2 * * ?}", zone = "Asia/Seoul")
    public void runStatisticsJob() throws Exception {
        log.info("통계 배치 작업 실행");
        runForDate(LocalDate.now(KOREA_ZONE).minusDays(1));
    }

    /** Historical dates use the same job identity on restart; completed dates are not forced to rerun. */
    public JobExecution runForDate(LocalDate targetDate) throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
                .addString(TARGET_DATE, targetDate.toString(), true)
                .toJobParameters();
        return jobLauncher.run(statisticsJob(), jobParameters);
    }

    private static void validateParameters(JobParameters parameters) throws JobParametersInvalidException {
        if (parameters == null || parameters.getParameters().size() != 1) {
            throw new JobParametersInvalidException("Only identifying targetDate is supported");
        }
        var date = parameters.getParameter(TARGET_DATE);
        if (date == null || date.getType() != String.class || !date.isIdentifying()) {
            throw new JobParametersInvalidException("targetDate must be an identifying String");
        }
        try {
            String value = (String) date.getValue();
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
            LocalDate.parse(value);
        } catch (RuntimeException e) {
            throw new JobParametersInvalidException("targetDate must be a valid ISO date (yyyy-MM-dd)");
        }
    }
}
