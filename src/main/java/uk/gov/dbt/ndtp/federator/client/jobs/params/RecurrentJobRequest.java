package uk.gov.dbt.ndtp.federator.client.jobs.params;

import lombok.Builder;
import lombok.Getter;
import uk.gov.dbt.ndtp.federator.client.jobs.Job;

@Builder
@Getter
public class RecurrentJobRequest {

    private JobParams jobParams;

    private Job job;
}
