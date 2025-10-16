package uk.gov.dbt.ndtp.federator.jobs.params;

import lombok.*;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;

import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

/**
 * Job parameter holder for client-initiated gRPC file exchange jobs.
 * <p>
 * Extends {@link ClientGRPCJobParams} with file exchange specific properties
 * that control how files are sent/received over the federator gRPC channel.
 * This class is a simple data container used by job handlers such as
 * {@code ClientGRPCFileExchangeJob} to initialise and execute a file
 * exchange operation.
 * </p>
 */
public class ClientFileExchangeGRPCJobParams extends ClientGRPCJobParams {
    
    private FileExchangeProperties fileExchangeProperties;

    public ClientFileExchangeGRPCJobParams(FileExchangeProperties fileExchangeProperties, String topic, ConnectionProperties config, String managementNodeId) {
        super(topic, config, managementNodeId);
        this.fileExchangeProperties = fileExchangeProperties;

    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ClientFileExchangeGRPCJobParams that = (ClientFileExchangeGRPCJobParams) o;
        return Objects.equals(getFileExchangeProperties(), that.getFileExchangeProperties());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getFileExchangeProperties());
    }

    @Override
    public String toString() {
        return super.toString();
    }




}
