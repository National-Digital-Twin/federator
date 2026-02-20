/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.service.config.exception;

/**
 * Exception indicating configuration fetch failure after retries or due to circuit breaker state.
 */
public class ConfigFetchException extends RuntimeException {
    public ConfigFetchException(String message) {
        super(message);
    }

    public ConfigFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
