/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.jobs.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.ConsumerConfigService;

class ScheduleAttributesResolverTest {

    private final ScheduleAttributesResolver resolver = new ScheduleAttributesResolver();

    @Test
    @DisplayName("resolveFrom: returns default when expression is null")
    void resolveFrom_nullExpression_returnsDefault() {
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder()
                .scheduleType("interval")
                .scheduleExpression(null)
                .build();

        ScheduleAttributesResolver.ScheduleAttributes attrs = resolver.resolveFrom(cfg);

        assertEquals(ScheduleAttributesResolver.EXPRESSION_TYPE_INTERVAL, attrs.getScheduleType());
        assertEquals("PT1H", attrs.getScheduleExpression());
    }

    @Test
    @DisplayName("resolveFrom: valid interval returns provided expression")
    void resolveFrom_validInterval_ok() {
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder()
                .scheduleType("interval")
                .scheduleExpression("PT5M")
                .build();

        ScheduleAttributesResolver.ScheduleAttributes attrs = resolver.resolveFrom(cfg);

        assertEquals("interval", attrs.getScheduleType());
        assertEquals("PT5M", attrs.getScheduleExpression());
    }

    @Test
    @DisplayName("resolveFrom: invalid interval falls back to default")
    void resolveFrom_invalidInterval_fallback() {
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder()
                .scheduleType("interval")
                .scheduleExpression("INVALID")
                .build();

        ScheduleAttributesResolver.ScheduleAttributes attrs = resolver.resolveFrom(cfg);

        assertEquals("interval", attrs.getScheduleType());
        assertEquals("PT1H", attrs.getScheduleExpression());
    }

    @Test
    @DisplayName("resolveFrom: valid cron returns provided expression")
    void resolveFrom_validCron_ok() {
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder()
                .scheduleType("cron")
                .scheduleExpression("0 0 * * * *")
                .build();

        ScheduleAttributesResolver.ScheduleAttributes attrs = resolver.resolveFrom(cfg);

        assertEquals("cron", attrs.getScheduleType());
        assertEquals("0 0 * * * *", attrs.getScheduleExpression());
    }

    @Test
    @DisplayName("resolveFrom: invalid cron falls back to default")
    void resolveFrom_invalidCron_fallback() {
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder()
                .scheduleType("cron")
                .scheduleExpression("* * * *") // too short
                .build();

        ScheduleAttributesResolver.ScheduleAttributes attrs = resolver.resolveFrom(cfg);

        assertEquals("interval", attrs.getScheduleType());
        assertEquals("PT1H", attrs.getScheduleExpression());
    }

    @Test
    @DisplayName("resolve: delegates to service and handles unknown type with default")
    void resolve_unknownType_defaults() {
        ConsumerConfigService service = mock(ConsumerConfigService.class);
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder()
                .scheduleType("weird")
                .scheduleExpression("PT10M")
                .build();
        when(service.getConsumerConfiguration()).thenReturn(cfg);

        ScheduleAttributesResolver.ScheduleAttributes attrs = resolver.resolve(service);

        assertEquals("interval", attrs.getScheduleType());
        assertEquals("PT1H", attrs.getScheduleExpression());
    }
}
