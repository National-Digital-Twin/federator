/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */
package uk.gov.dbt.ndtp.federator.jobs.schedule;

import java.time.Duration;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.cron.CronExpression;
import uk.gov.dbt.ndtp.federator.jobs.JobsConstants;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.ConsumerConfigService;

/**
 * Resolves and validates schedule attributes for background jobs.
 * <p>
 * This component centralises the logic used to interpret the schedule configuration returned by
 * Management Node. It ensures that the chosen schedule type (cron or interval) and its
 * corresponding expression are valid. When invalid or missing, a safe default is returned.
 * </p>
 *
 * <h3>Validation rules</h3>
 * <ul>
 *   <li>Interval: must be a valid ISO-8601 duration parsable by {@link Duration#parse(CharSequence)}.</li>
 *   <li>Cron: must be a valid cron expression recognised by {@link CronExpression}.</li>
 *   <li>Missing/null expression: falls back to {@link #defaultAttributes()}.</li>
 * </ul>
 */
@Slf4j
public class ScheduleAttributesResolver {

    public static final String EXPRESSION_TYPE_CRON = "cron";
    public static final String EXPRESSION_TYPE_INTERVAL = "interval";

    /**
     * Attributes DTO representing a validated schedule expression and its type.
     */
    @Builder
    @Data
    public static class ScheduleAttributes {
        private final String scheduleExpression;
        private final String scheduleType;
    }

    /**
     * Resolve schedule attributes using the provided configuration service.
     *
     * @param configService service to obtain the consumer configuration from
     * @return validated schedule attributes, or defaults if invalid/missing
     */
    public ScheduleAttributes resolve(final ConsumerConfigService configService) {
        final ConsumerConfigDTO cfg = configService.getConsumerConfiguration();
        return resolveFrom(cfg);
    }

    /**
     * Resolve schedule attributes from a {@link ConsumerConfigDTO}.
     *
     * @param cfg consumer configuration holding schedule data
     * @return validated schedule attributes, or defaults if invalid/missing
     */
    public ScheduleAttributes resolveFrom(final ConsumerConfigDTO cfg) {
        final String expression = cfg.getScheduleExpression();
        final String type = cfg.getScheduleType();

        if (expression == null) {
            log.warn(
                    "No schedule expression found, using default value of {}",
                    JobsConstants.DEFAULT_DURATION_EVERY_HOUR);
            return defaultAttributes();
        }

        if (type != null && type.equalsIgnoreCase(EXPRESSION_TYPE_INTERVAL)) {
            return validateInterval(expression);
        }
        if (type != null && type.equalsIgnoreCase(EXPRESSION_TYPE_CRON)) {
            return validateCron(expression);
        }

        // Unknown type: fall back to default and log.
        log.error(
                "Unknown schedule type '{}', using default value of {}",
                type,
                JobsConstants.DEFAULT_DURATION_EVERY_HOUR);
        return defaultAttributes();
    }

    /**
     * Validate ISO-8601 interval expressions.
     */
    ScheduleAttributes validateInterval(final String expression) {
        try {
            Duration.parse(expression);
        } catch (Exception e) {
            log.error(
                    "Invalid schedule expression for type 'interval' {}, using default value of {}",
                    expression,
                    JobsConstants.DEFAULT_DURATION_EVERY_HOUR);
            return defaultAttributes();
        }
        return ScheduleAttributes.builder()
                .scheduleType(EXPRESSION_TYPE_INTERVAL)
                .scheduleExpression(expression)
                .build();
    }

    /**
     * Validate cron expressions.
     */
    ScheduleAttributes validateCron(final String expression) {
        try {
            final CronExpression cronExpression = new CronExpression(expression);
            cronExpression.validate();
        } catch (Exception e) {
            log.error(
                    "Invalid schedule expression for type 'cron' {}, using default value of {}",
                    expression,
                    JobsConstants.DEFAULT_DURATION_EVERY_HOUR);
            return defaultAttributes();
        }
        return ScheduleAttributes.builder()
                .scheduleType(EXPRESSION_TYPE_CRON)
                .scheduleExpression(expression)
                .build();
    }

    /**
     * Default schedule attributes used as a safe fallback.
     * The default is an hourly interval defined by {@link JobsConstants#DEFAULT_DURATION_EVERY_HOUR}.
     */
    public ScheduleAttributes defaultAttributes() {
        return ScheduleAttributes.builder()
                .scheduleExpression(JobsConstants.DEFAULT_DURATION_EVERY_HOUR)
                .scheduleType(EXPRESSION_TYPE_INTERVAL)
                .build();
    }
}
