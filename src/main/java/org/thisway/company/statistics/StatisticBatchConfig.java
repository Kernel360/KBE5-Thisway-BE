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
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.thisway.company.intrastructure.CompanyRepository;
import org.thisway.company.statistics.application.StatisticService;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Configuration
@EnableBatchProcessing
@EnableScheduling
@RequiredArgsConstructor
public class StatisticBatchConfig {

    private final StatisticService statisticService;
    private final CompanyRepository companyRepository;
    private final JobLauncher jobLauncher;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job statisticsJob() {
        return new JobBuilder("statisticsJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(statisticsStep())
                .build();
    }

    @Bean
    public Step statisticsStep() {
        return new StepBuilder("statisticsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    LocalDate targetDate = LocalDate.now().minusDays(1);
                    List<Long> companyIds = companyRepository.findAllActiveCompanyIds();
                    for (Long companyId : companyIds) {
                        try {
                            statisticService.saveStatistics(companyId, targetDate);
                            log.info("회사 ID {}의 통계 저장 성공", companyId);
                        } catch (Exception e) {
                            log.error("회사 ID {}의 통계 저장 실패: {}", companyId, e.getMessage(), e);
                        }
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void runStatisticsJob() throws Exception {
        log.info("통계 배치 작업 실행");
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(statisticsJob(), jobParameters);
    }
}
