/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.service.config.exception;

import uk.gov.dbt.ndtp.federator.exceptions.RebuildableRuntimeException;

/**
 * Exception indicating configuration fetch failure after retries or due to circuit breaker state.
 */
public class ConfigFetchException extends RebuildableRuntimeException {
    public ConfigFetchException(String message) {
        super(message);
    }

    public ConfigFetchException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigFetchException(
            String message, Throwable cause, String componentName, String operation, String targetId) {
        super(message, cause, componentName, operation, targetId);
    }

    /**
     * Rebuilds this exception with the given message and cause.
     * @param message the enriched error message
     * @param cause the original exception
     * @return a new instance of {@link ConfigFetchException}
     */
    @Override
    public ConfigFetchException rebuild(String message, Throwable cause) {
        return new ConfigFetchException(
                message, cause, this.getComponentName(), this.getOperation(), this.getTargetId());
    }
}
