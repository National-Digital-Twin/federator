/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.client.jobs;

/**
 * Utility class that holds constants for job scheduling.
 * <p>
 * This class is not meant to be instantiated.
 */
public final class JobsConstants {

    /**
     * Default ISO-8601 duration for a recurring job that runs every hour.
     * Example: PT1H means a period of time of 1 hour.
     */
    public static final String DEFAULT_DURATION_EVERY_HOUR = "PT1H"; // 1 hour

    public static final int RETRIES = 5;
    public static final String DAEMON_JOB = "DynamicConfigJob";
    public static final String DAEMON_JOB_NAME = "DynamicConfigProvider";

    private JobsConstants() {
        // Prevent instantiation
        throw new AssertionError("Cannot instantiate utility class");
    }
}
