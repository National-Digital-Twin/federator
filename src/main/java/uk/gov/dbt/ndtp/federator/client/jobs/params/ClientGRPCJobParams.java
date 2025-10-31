package uk.gov.dbt.ndtp.federator.client.jobs.params;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientGRPCJobParams extends JobParams {

    private String topic;

    public ClientGRPCJobParams(String topic, ConnectionProperties config, String managementNodeId) {
        super();
        this.topic = topic;
        this.setConnectionProperties(config);
        this.setManagementNodeId(managementNodeId);
    }

    @Override
    public String getJobId() {
        return getJobName() + "-" + topic;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
