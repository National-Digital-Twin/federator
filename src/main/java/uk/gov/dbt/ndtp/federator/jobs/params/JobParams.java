package uk.gov.dbt.ndtp.federator.jobs.params;

import java.util.Objects;
import lombok.*;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JobParams {

    private String jobId;

    private ConnectionProperties connectionProperties;

    private Boolean requireImmediateTrigger = false;

    private String jobName;

    private String managementNodeId;

    private Integer amountOfRetries = 5; // default value

    private String jobScheduleType;

    private String scheduleExpression;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        JobParams jobParams = (JobParams) o;
        return Objects.equals(getJobId(), jobParams.getJobId())
                && Objects.equals(getConnectionProperties(), jobParams.getConnectionProperties())
                && Objects.equals(getJobName(), jobParams.getJobName())
                && Objects.equals(getManagementNodeId(), jobParams.getManagementNodeId())
                && Objects.equals(getAmountOfRetries(), jobParams.getAmountOfRetries())
                && Objects.equals(getJobScheduleType(), jobParams.getJobScheduleType())
                && Objects.equals(getScheduleExpression(), jobParams.getScheduleExpression())
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
                getJobScheduleType(),
                getScheduleExpression(),
                getRequireImmediateTrigger());
    }

    @Override
    public String toString() {
        return this.jobName;
    }
}
