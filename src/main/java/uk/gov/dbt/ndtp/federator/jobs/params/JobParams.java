package uk.gov.dbt.ndtp.federator.jobs.params;

import java.time.Duration;
import java.util.Objects;
import lombok.*;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JobParams {

    public String jobId;

    private ConnectionProperties connectionProperties;

    private Boolean requireImmediateTrigger = false;

    private String jobName;

    private String managementNodeId;

    private Integer AmountOfRetries = 5; // default value

    private Duration duration = Duration.ofSeconds(30);

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        JobParams jobParams = (JobParams) o;
        return Objects.equals(getJobId(), jobParams.getJobId())
                && Objects.equals(getConnectionProperties(), jobParams.getConnectionProperties())
                && Objects.equals(getJobName(), jobParams.getJobName())
                && Objects.equals(getManagementNodeId(), jobParams.getManagementNodeId())
                && Objects.equals(getAmountOfRetries(), jobParams.getAmountOfRetries())
                && Objects.equals(getDuration(), jobParams.getDuration())
                && Objects.equals(getRequireImmediateTrigger(), jobParams.getRequireImmediateTrigger());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getJobId(),
                getConnectionProperties(),
                getJobName(),
                getManagementNodeId(),
                getAmountOfRetries(),
                getDuration(),
                getRequireImmediateTrigger());
    }

    @Override
    public String toString() {
        return this.jobName;
    }
}
