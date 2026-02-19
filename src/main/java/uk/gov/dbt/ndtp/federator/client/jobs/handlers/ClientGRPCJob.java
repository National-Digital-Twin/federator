package uk.gov.dbt.ndtp.federator.client.jobs.handlers;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.ToLongBiFunction;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.WrappedGRPCClient;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.client.grpc.GRPCTopicClient;
import uk.gov.dbt.ndtp.federator.client.jobs.Job;
import uk.gov.dbt.ndtp.federator.client.jobs.params.ClientGRPCJobParams;
import uk.gov.dbt.ndtp.federator.client.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.RedisUtil;
import uk.gov.dbt.ndtp.federator.exceptions.ClientGRPCJobException;

@Slf4j
public class ClientGRPCJob implements Job {

    static final String KAFKA_TOPIC_PREFIX = ".topic.prefix";

    // Injected collaborators for testability (property-settable)
    @Setter
    private Supplier<String> prefixSupplier;

    @Setter
    private ToLongBiFunction<String, String> offsetProvider;

    @Setter
    private BiFunction<ConnectionProperties, String, WrappedGRPCClient> clientFactory;

    @Setter
    private ClientGRPCJobParams request;

    /** Default constructor wires real implementations for backward compatibility. */
    public ClientGRPCJob() {
        this.prefixSupplier = () -> PropertyUtil.getPropertyValue(KAFKA_TOPIC_PREFIX, "");
        this.offsetProvider = (prefix, topic) -> RedisUtil.getInstance().getOffset(prefix, topic);
        this.clientFactory = (config, prefix) -> new WrappedGRPCClient(new GRPCTopicClient(config, prefix));
    }

    /** Convenience constructor to set initial request using default wiring. */
    public ClientGRPCJob(ClientGRPCJobParams request) {
        this();
        this.request = request;
    }

    @Override
    public void run(JobParams value) {
        final String prefix = prefixSupplier.get();

        if (request == null) {
            request = (ClientGRPCJobParams) value;
        }

        ConnectionProperties connectionProperties = request.getConnectionProperties();
        log.info(
                "Calling GRPC endpoint of producer:{} , Topic {}",
                connectionProperties.serverHost(),
                request.getTopic());

        try (WrappedGRPCClient grpcClient = clientFactory.apply(connectionProperties, prefix)) {
            long offset = offsetProvider.applyAsLong(grpcClient.getRedisPrefix(), request.getTopic());
            grpcClient.processTopic(request.getTopic(), offset);
        } catch (Exception e) {
            throw new ClientGRPCJobException("Failed to process topic '" + request.getTopic() + "' via GRPC client", e);
        }
    }

    @Override
    public String toString() {
        return "Client GRPC Job";
    }
}
