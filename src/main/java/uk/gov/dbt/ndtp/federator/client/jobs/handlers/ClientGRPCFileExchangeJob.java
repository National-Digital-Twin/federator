package uk.gov.dbt.ndtp.federator.client.jobs.handlers;

import java.util.function.Supplier;
import java.util.function.ToLongBiFunction;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.grpc.GRPCFileClient;
import uk.gov.dbt.ndtp.federator.client.jobs.Job;
import uk.gov.dbt.ndtp.federator.client.jobs.params.ClientFileExchangeGRPCJobParams;
import uk.gov.dbt.ndtp.federator.client.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.RedisUtil;
import uk.gov.dbt.ndtp.federator.exceptions.ClientGRPCJobException;

@Slf4j
public class ClientGRPCFileExchangeJob implements Job {

    private static final String KAFKA_TOPIC_PREFIX = ".topic.prefix";
    private Supplier<String> prefixSupplier;
    private ClientFileExchangeGRPCJobParams request;
    private ToLongBiFunction<String, String> offsetProvider;

    /** Default constructor wires real implementations for backward compatibility. */
    public ClientGRPCFileExchangeJob() {
        this.prefixSupplier = () -> PropertyUtil.getPropertyValue(KAFKA_TOPIC_PREFIX, "");
        this.offsetProvider = (prefix, topic) -> RedisUtil.getInstance().getOffset(prefix, topic);
    }

    /** Convenience constructor to set initial request using default wiring. */
    public ClientGRPCFileExchangeJob(ClientFileExchangeGRPCJobParams request) {
        this();
        this.request = request;
    }

    @Override
    public void run(JobParams value) {
        if (request == null) {
            request = (ClientFileExchangeGRPCJobParams) value;
        }

        log.info("running File Exchange Job:");
        String topic = request.getTopic();
        String destinationPath = request.getFileExchangeProperties().getDestinationPath();

        if (destinationPath == null || destinationPath.isBlank()) {
            String msg = "Destination path is required but was null/blank for topic '" + topic + "'";
            throw new ClientGRPCJobException(new IllegalArgumentException(msg));
        }

        log.info(
                "requesting topic:{}, source:{}, destination:{}",
                topic,
                request.getFileExchangeProperties().getSourcePath(),
                destinationPath);

        try (GRPCFileClient grpcClient = new GRPCFileClient(request.getConnectionProperties(), prefixSupplier.get())) {
            long offset = offsetProvider.applyAsLong(grpcClient.getRedisPrefix(), topic);
            log.info("offset:{} , topic:{}", offset, topic);
            grpcClient.processTopic(topic, offset, destinationPath);
        } catch (Exception e) {
            throw new ClientGRPCJobException("Failed to process topic '" + topic + "' via GRPC client", e);
        }
    }

    @Override
    public String toString() {
        return "Client File Exchange Over GRPC Job";
    }
}
