package uk.gov.dbt.ndtp.federator.client.jobs;

import uk.gov.dbt.ndtp.federator.client.jobs.params.JobParams;

/**
 * Represents a runnable job that performs specific operations, accepting parameters for execution.
 * Implementations of this interface are expected to define the logic of the job
 * inside the {@link #run(JobParams)} method. The execution parameters are passed
 * via the {@link JobParams} object.
 */
public interface Job {

    /**
     * Executes the defined job logic using the provided parameters.
     *
     * @param value the job parameters*/
    void run(JobParams value);
}
