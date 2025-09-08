// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

package uk.gov.dbt.ndtp.federator.jobs;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.configuration.JobRunr;
import org.jobrunr.jobs.RecurringJob;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.RecurringJobBuilder;
import org.jobrunr.storage.AbstractStorageProvider;
import org.jobrunr.storage.InMemoryStorageProvider;
import uk.gov.dbt.ndtp.federator.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.jobs.params.RecurrentJobRequest;
import uk.gov.dbt.ndtp.federator.lifecycle.ShutdownThread;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

/**
 * Singleton provider to configure and manage the lifecycle of JobRunr background job scheduler.
 * <p>
 * Implements {@link JobSchedulerProvider} to provide a stable abstraction for components that need
 * to ensure the scheduler is running or to stop it during shutdown.
 * </p>
 * <p>
 * All scheduler configuration is centralised here so that both FederatorClient and FederatorServer
 * can reliably initialise and use the same JobRunr instance.
 * </p>
 * <p>
 * Configuration (optional, with defaults shown):
 * <ul>
 *   <li>jobs.dashboard.enabled = true</li>
 *   <li>jobs.background.enabled = true</li>
 *   <li>jobs.storage.provider = memory</li>
 * </ul>
 * Currently only the in-memory storage provider is supported without additional dependencies.
 * </p>
 */
@Slf4j
public final class DefaultJobSchedulerProvider implements JobSchedulerProvider {

    public static final String CONSTANT_PROVIDER_TYPE_MEMORY = "memory";
    // Property keys
    private static final String PROP_DASHBOARD_ENABLED = "jobs.dashboard.enabled";
    private static final String PROP_DASHBOARD_PORT = "jobs.dashboard.port";
    private static final String PROP_BACKGROUND_ENABLED = "jobs.background.enabled";
    private static final String PROP_STORAGE_PROVIDER = "jobs.storage.provider"; // memory (default), future: redis, sql
    private static volatile DefaultJobSchedulerProvider instance;

    private final Object lifecycleLock = new Object();
    private boolean started = false;

    // Keep reference so we can close when stopping (for in-memory case)
    private AbstractStorageProvider storageProvider;

    private JobScheduler jobScheduler;
    private RecurringJobsAccess recurringJobsAccess;

    private DefaultJobSchedulerProvider() {
        // hidden
    }

    /**
     * Factory for external dependency injection (e.g., tests or custom environments).
     * Returns a non-singleton instance wired with provided dependencies.
     */
    public static DefaultJobSchedulerProvider withDependencies(
            final JobScheduler scheduler, final AbstractStorageProvider storage, final RecurringJobsAccess jobsAccess) {
        DefaultJobSchedulerProvider instance = new DefaultJobSchedulerProvider();
        synchronized (instance.lifecycleLock) {
            instance.jobScheduler = scheduler;
            instance.storageProvider = storage;
            instance.recurringJobsAccess = (jobsAccess != null) ? jobsAccess : instance.defaultAccess();
            instance.started = true;
        }
        return instance;
    }

    public static DefaultJobSchedulerProvider getInstance() {
        if (instance == null) {

            synchronized (DefaultJobSchedulerProvider.class) {
                if (instance == null) {
                    instance = new DefaultJobSchedulerProvider();
                }
            }
        }

        return instance;
    }

    /**
     * Ensure the JobRunr scheduler is started once. Safe for repeated calls.
     */
    @Override
    public void ensureStarted() {
        if (started) {
            return;
        }
        synchronized (lifecycleLock) {

            // Read configuration with defaults
            boolean dashboardEnabled = PropertyUtil.getPropertyBooleanValue(PROP_DASHBOARD_ENABLED, "false");
            boolean backgroundEnabled = PropertyUtil.getPropertyBooleanValue(PROP_BACKGROUND_ENABLED, "true");
            String storage = PropertyUtil.getPropertyValue(PROP_STORAGE_PROVIDER, CONSTANT_PROVIDER_TYPE_MEMORY)
                    .trim()
                    .toLowerCase();
            int dashboardPort = PropertyUtil.getPropertyIntValue(PROP_DASHBOARD_PORT, "8080");

            // Only memory supported by default. If unsupported, fall back to in-memory to keep tests and runtime
            // stable.
            if (!CONSTANT_PROVIDER_TYPE_MEMORY.equals(storage)) {
                log.warn("Unsupported jobs.storage.provider='{}', falling back to 'memory'", storage);
            }
            storageProvider = new InMemoryStorageProvider();

            // set default recurring jobs access if not provided externally
            if (recurringJobsAccess == null) {
                recurringJobsAccess = defaultAccess();
            }

            var cfg = JobRunr.configure().useStorageProvider(storageProvider);
            if (backgroundEnabled) {
                cfg = cfg.useBackgroundJobServer();
            }
            if (dashboardEnabled) {
                cfg = cfg.useDashboard(dashboardPort);
            }
            jobScheduler = cfg.initialize().getJobScheduler();

            started = true;

            log.info(
                    "JobRunr initialised (storage={}, background={}, dashboard={})",
                    CONSTANT_PROVIDER_TYPE_MEMORY,
                    backgroundEnabled,
                    dashboardEnabled);

            // Register a shutdown task
            ShutdownThread.register(() -> {
                shutdown();
                log.info("JobRunr stopped");
            });
        }
    }

    private void shutdown() {
        try {
            JobRunr.destroy();
        } catch (Exception e) {
            log.debug("Ignoring exception while destroying JobRunr", e);
        }
        try {
            if (storageProvider != null) {
                storageProvider.close();
            }
        } catch (Exception e) {
            log.debug("Ignoring exception while closing storage provider", e);
        }
    }

    private RecurringJobsAccess defaultAccess() {
        return new RecurringJobsAccess() {
            @Override
            public Map<String, JobParams> paramsById(AbstractStorageProvider sp) {
                Map<String, JobParams> map = new java.util.HashMap<>();
                for (RecurringJob r : sp.getRecurringJobs()) {
                    JobParams p = extractJobParamsFrom(r);
                    if (p != null) map.put(r.getId(), p);
                }
                return map;
            }

            @Override
            public java.util.Set<String> ids(AbstractStorageProvider sp) {
                return sp.getRecurringJobs().stream()
                        .map(RecurringJob::getId)
                        .collect(java.util.stream.Collectors.toSet());
            }
        };
    }

    /** Stops the scheduler if running. Normally invoked via the registered shutdown hook. */
    @Override
    public void stop() {
        synchronized (lifecycleLock) {
            if (!started) return;
            shutdown();
            started = false;
            log.info("JobRunr stopped (explicit)");
        }
    }

    /**
     * Obtain the JobRunr JobScheduler instance.
     * <p>
     * This method is only valid after {@link #ensureStarted()} has successfully initialised the scheduler.
     * It will return the same instance for the lifetime of this provider until {@link #stop()} is called.
     * </p>
     *
     * @return the active JobScheduler instance
     * @throws IllegalStateException if called before the scheduler has been started or after it has been stopped
     */
    @Override
    public JobScheduler getJobScheduler() {
        if (started) return jobScheduler;
        else throw new IllegalStateException("JobScheduler not started");
    }

    @Override
    public void registerJob(Job job, JobParams params) {

        if (job == null) throw new IllegalArgumentException("Job cannot be null");

        if (params == null) throw new IllegalArgumentException("Params cannot be null");

        if (params.getRequireImmediateTrigger() != null && params.getRequireImmediateTrigger()) {
            log.info("Registering job {} with immediate trigger", params.getJobName());
            UUID id = UUID.randomUUID();
            jobScheduler.enqueue(id, () -> job.run(params));
            log.info("Enqueued immediate job name={} id={}", params.getJobName(), id);
        }

        log.info("Registering recurrent job {} ", params.getJobName());
        RecurringJobBuilder recurringJobBuilder = RecurringJobBuilder.aRecurringJob();
        recurringJobBuilder
                .withInterval(params.getDuration())
                .withId(params.getJobId())
                .withZoneId(ZoneId.of("Europe/London"))
                .withDetails(() -> job.run(params));

        if (params.getAmountOfRetries() != null && params.getAmountOfRetries() > 0)
            recurringJobBuilder.withAmountOfRetries(params.getAmountOfRetries());

        jobScheduler.createRecurrently(recurringJobBuilder);
        log.info(
                "Registered recurring job id={} name={} interval={} retries={}",
                params.getJobId(),
                params.getJobName(),
                params.getDuration(),
                params.getAmountOfRetries());
    }

    @Override
    public void removeRecurringJob(String jobId) {
        log.info("Removing recurring job {}", jobId);
        jobScheduler.deleteRecurringJob(jobId);
        log.info("Deleted recurring job {}", jobId);
    }

    @Override
    public void reloadRecurrentJobs(final String managementNodeId, final List<RecurrentJobRequest> requests) {
        Objects.requireNonNull(managementNodeId, "Management Node Id can not be null");
        Objects.requireNonNull(requests, "params can not be null");

        // 1) Build a quick lookup of the requested jobs for this management node
        //    Key = jobId, Value = JobParams
        final Map<String, JobParams> requestedForNode = buildRequestedJobMapForNode(managementNodeId, requests);

        // 2) Remove jobs for this node that are no longer requested or whose params changed
        //    - Skip jobs that belong to other nodes
        //    - Skip jobs if we cannot safely read their JobParams
        removeObsoleteOrModifiedJobs(managementNodeId, requestedForNode);

        // 3) Add any requested jobs for this node that are not currently scheduled
        addMissingJobs(managementNodeId, requests);
    }

    /**
     * Builds a map of requested recurring jobs for a specific management node.
     */
    private Map<String, JobParams> buildRequestedJobMapForNode(
            final String managementNodeId, final List<RecurrentJobRequest> requests) {
        return requests.stream()
                .map(RecurrentJobRequest::getJobParams)
                .filter(Objects::nonNull)
                .filter(p -> managementNodeId.equals(p.getManagementNodeId()))
                .collect(java.util.stream.Collectors.toMap(
                        JobParams::getJobId, java.util.function.Function.identity(), (a, b) -> b));
    }

    /**
     * Remove recurring jobs that either:
     * - are not present in the new requests for this node (Remove), or
     * - have different parameters compared to the requested ones (Remove), or
     * - belong to another node (Skip), or
     * - have unreadable params (Skip for safety).
     * If a job is unchanged, we Skip it (keep it as-is).
     */
    private void removeObsoleteOrModifiedJobs(
            final String managementNodeId, final Map<String, JobParams> requestedForNode) {
        final java.util.Map<String, JobParams> existingMap = getExistingJobsMap();

        for (java.util.Map.Entry<String, JobParams> entry : existingMap.entrySet()) {
            final String existingId = entry.getKey();
            final JobParams existingParams = entry.getValue();

            final boolean manageable =
                    existingParams != null && managementNodeId.equals(existingParams.getManagementNodeId());

            if (!manageable) {
                log.warn("Skipping recurring job id={} for management node={} (not manageable)", existingId, managementNodeId);
                continue;
            }
                final JobParams requestedParams = requestedForNode.get(existingId);
                if (requestedParams == null) {
                    // Not requested anymore -> Remove
                    log.info(
                            "Deleting recurring job not present in requests id={} for management node={}",
                            existingId,
                            managementNodeId);
                    removeRecurringJob(existingId);
                } else {
                    boolean  unchanged = requestedParams.equals(existingParams);

                    if (!unchanged) {
                        // Parameters changed -> Remove (we will add it back with new params in the Add phase)
                        log.info(
                                "Deleting modified recurring job with id={} for management node={}",
                                existingId,
                                managementNodeId);
                        removeRecurringJob(existingId);
                    } else {
                        // Parameters unchanged -> Skip
                        log.debug(
                                "Skipping unchanged recurring job id={} for management node={} (same hash/equality)",
                                existingId,
                                managementNodeId);
                    }
                }

        }
    }

    /**
     * Adds any requested jobs for the node that are not currently scheduled after the removal phase.
     * - Add: job requested for this node but not present after removals
     * - Skip: job already present (unchanged) or belongs to a different node
     */
    private void addMissingJobs(final String managementNodeId, final List<RecurrentJobRequest> requests) {
        final java.util.Set<String> existingIdsAfterDeletion = recurringJobsAccess.ids(storageProvider);

        for (RecurrentJobRequest req : requests) {
            final JobParams jobParams = req.getJobParams();
            final boolean manageable = jobParams != null && managementNodeId.equals(jobParams.getManagementNodeId());
            if (manageable) {
                final String id = jobParams.getJobId();
                final boolean alreadyPresent = existingIdsAfterDeletion.contains(id);
                if (!alreadyPresent) {
                    try {
                        // Add
                        log.info(
                                "Registering missing/updated recurring job id={} for management node={}",
                                id,
                                managementNodeId);
                        registerJob(req.getJob(), jobParams);
                    } catch (Exception e) {
                        log.warn(
                                "Failed to register recurring job id={} (management node={})", id, managementNodeId, e);
                    }
                }
            }
        }
    }

    /**
     * Safely extracts JobParams from a JobRunr RecurringJob. If extraction fails, returns null.
     */
    private JobParams extractJobParamsFrom(final RecurringJob existing) {
        try {
            final var details = existing.getJobDetails();
            final var params = (details != null) ? details.getJobParameters() : null;
            if (params != null && !params.isEmpty()) {
                final Object obj = params.getFirst().getObject();
                if (obj instanceof JobParams jobParams) {
                    return jobParams;
                }
            }
        } catch (Exception e) {
            log.debug("Could not inspect existing job parameters for id={}: {}", existing.getId(), e.toString());
        }
        return null;
    }

    private java.util.Map<String, JobParams> getExistingJobsMap() {
        return new java.util.HashMap<>(recurringJobsAccess.paramsById(storageProvider));
    }

    // Strategy to retrieve existing recurring jobs and their parameters
    public interface RecurringJobsAccess {
        Map<String, JobParams> paramsById(AbstractStorageProvider storageProvider);

        java.util.Set<String> ids(AbstractStorageProvider storageProvider);
    }
}
