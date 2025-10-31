// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

package uk.gov.dbt.ndtp.federator.client.jobs;

import java.util.List;
import org.jobrunr.scheduling.JobScheduler;
import uk.gov.dbt.ndtp.federator.client.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.client.jobs.params.RecurrentJobRequest;

/**
 * Abstraction for a background job scheduler lifecycle.
 * <p>
 * This interface exists to decouple components (e.g. FederatorClient and FederatorServer)
 * from a specific scheduler implementation (currently JobRunr via {@link DefaultJobSchedulerProvider}).
 * It enables easier testing and future replacement of the scheduling backend without
 * changing the dependent code.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>{@link #ensureStarted()} starts/initialises the scheduler if it has not been started yet.
 *       Repeated calls are safe (idempotent) and should not reinitialise the scheduler.</li>
 *   <li>{@link #stop()} stops the scheduler if it is currently running and releases owned resources.</li>
 * </ul>
 */
public interface JobSchedulerProvider {

    /**
     * Ensure the job scheduler is started.
     * Implementations must be safe for repeated calls and must not throw if already started.
     */
    void ensureStarted();

    /**
     * Stop the job scheduler and release resources if running. Safe to call when not started.
     */
    void stop();

    /**
     * Returns the underlying JobScheduler instance once the scheduler has been started.
     * Contract:
     * - Must be called after {@link #ensureStarted()} or it will throw an {@link IllegalStateException}.
     * - Implementations should return a stable instance for the lifetime of the provider until {@link #stop()} is called.
     * - After {@link #stop()}, this method should again throw {@link IllegalStateException} until restarted.
     *
     * @return a non-null JobScheduler instance when the provider is started
     * @throws IllegalStateException if the provider has not been started yet
     */
    JobScheduler getJobScheduler();

    /**
     * Registers a job with the job scheduler. The job can be configured to trigger immediately
     * or be scheduled as a recurring job based on the provided job properties.
     *
     * @param job the job instance containing execution logic and scheduling details
     *            such as name, ID, retries, duration, and whether an immediate trigger is required.
     *            Cannot be null.
     * @throws IllegalArgumentException if the provided job is null
     */
    void registerJob(Job job, JobParams value);

    /**
     * Removes a recurring job from the job scheduler.
     *
     * @param jobId the unique identifier of the recurring job to be removed.
     *              Must not be null or empty.
     */
    void removeRecurringJob(String jobId);

    /**
     * Reloads the recurrent jobs for a given management node. This method is used to
     * update or initialize recurring job configurations associated with a specific
     * management node.
     *
     * @param managementNodeId the identifier of the management node for which the jobs
     *                         should be reloaded. Must not be null or empty.
     * @param requests a list of job requests containing the job definitions and their
     *             associated parameters. Must not be null.
     */
    void reloadRecurrentJobs(String managementNodeId, List<RecurrentJobRequest> requests);
}
