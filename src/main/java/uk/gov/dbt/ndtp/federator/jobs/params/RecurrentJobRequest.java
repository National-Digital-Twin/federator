package uk.gov.dbt.ndtp.federator.jobs.params;

import lombok.Builder;
import lombok.Getter;
import uk.gov.dbt.ndtp.federator.jobs.Job;

@Builder
@Getter
public class RecurrentJobRequest {

  private JobParams jobParams;

  private Job job;
}
