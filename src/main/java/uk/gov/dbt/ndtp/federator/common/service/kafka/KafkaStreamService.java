package uk.gov.dbt.ndtp.federator.common.service.kafka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.model.dto.AttributesDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ConsumerDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProductDTO;
import uk.gov.dbt.ndtp.federator.common.service.stream.FederatorStreamService;
import uk.gov.dbt.ndtp.federator.common.utils.ThreadUtil;
import uk.gov.dbt.ndtp.federator.server.conductor.MessageConductor;
import uk.gov.dbt.ndtp.federator.server.conductor.RdfMessageConductor;
import uk.gov.dbt.ndtp.federator.server.consumer.ClientTopicOffsets;
import uk.gov.dbt.ndtp.federator.server.grpc.GRPCContextKeys;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.grpc.TopicRequest;

public class KafkaStreamService implements FederatorStreamService<TopicRequest, KafkaByteBatch> {
    public static final Logger LOGGER = LoggerFactory.getLogger("KafkaStreamService");
    private static final ExecutorService THREADED_EXECUTOR = ThreadUtil.threadExecutor("KafkaStream");
    private final Set<String> sharedHeaders;

    public KafkaStreamService(Set<String> sharedHeaders) {
        this.sharedHeaders = sharedHeaders;
    }

    /**
     * Takes a request with the topic, client id, key, offset and the streamObservable object to write
     * into.
     *
     * @param request          that contains the details required to get data from a specific topic.
     * @param streamObservable used to write the data into.
     * @throws InvalidTopicException if the topic is not valid for a specific client.
     */
    @Override
    public void streamToClient(TopicRequest request, StreamObservable<KafkaByteBatch> streamObservable)
            throws InvalidTopicException {
        String topic = request.getTopic();
        long offset = request.getOffset();
        String consumerId = GRPCContextKeys.CLIENT_ID.get();
        streamObservable.setOnCancelHandler(() -> LOGGER.info("Cancel called by client: {}", consumerId));

        ProducerConfigDTO producerConfigDTO = getProducerConfiguration();

        if (!hasConsumerAccessToTopic(consumerId, topic, producerConfigDTO)) {
            String errMsg = String.format("Topic (%s) is not valid for client (%s).", topic, consumerId);
            LOGGER.error(errMsg);
            throw new InvalidTopicException(errMsg);
        }

        List<AttributesDTO> filterAttributes = getFilterAttributesForConsumer(consumerId, topic, producerConfigDTO);
        ClientTopicOffsets topicData = new ClientTopicOffsets(consumerId, topic, offset);
        MessageConductor messageConductor =
                new RdfMessageConductor(topicData, streamObservable, filterAttributes, this.sharedHeaders);
        List<Future<?>> futures = new ArrayList<>();
        futures.add(THREADED_EXECUTOR.submit(messageConductor::processMessages));
        LOGGER.info(
                "Awaiting TopicRequest finished for Client: {}, Topic: {}, Offset: {}",
                consumerId,
                topicData.getTopic(),
                topicData.getOffset());
        ThreadUtil.awaitShutdown(futures, messageConductor, THREADED_EXECUTOR);
        LOGGER.info(
                "Finished TopicRequest processed for Client: {}, Topic: {}, Offset: {}",
                consumerId,
                topicData.getTopic(),
                topicData.getOffset());

        streamObservable.onCompleted();
    }

    /**
     * Retrieves filter attributes for a consumer for a given topic from the provided producer configuration.
     *
     * <p>Traversal logic:
     * <ul>
     *   <li>Iterate all producers in the configuration (null-safe)</li>
     *   <li>From each producer, look at all products whose topic matches the supplied topic</li>
     *   <li>From matching products, find consumers whose idpClientId matches the supplied consumerId (case-insensitive)</li>
     *   <li>Collect and return all non-null attributes for those matching consumers</li>
     * </ul>
     *
     * <p>If the configuration or any nested collection is null, this method safely returns an empty list.
     *
     * @param consumerId the consumer's IDP client id to match (case-insensitive)
     * @param topic the Kafka topic to match against products
     * @param producerConfigDTO the producer configuration to search
     * @return a list of matching AttributesDTO; empty if none found or inputs are missing
     */
    private List<AttributesDTO> getFilterAttributesForConsumer(
            String consumerId, String topic, ProducerConfigDTO producerConfigDTO) {
        if (producerConfigDTO == null || producerConfigDTO.getProducers() == null) {
            LOGGER.warn("Producer configuration or producers list is null when retrieving filter attributes.");
            return Collections.emptyList();
        }

        List<AttributesDTO> attributes = producerConfigDTO.getProducers().stream()
                .filter(Objects::nonNull)
                .flatMap(producer -> {
                    List<ProductDTO> products = producer.getProducts();
                    return products == null ? Stream.empty() : products.stream();
                })
                .filter(product -> product != null
                        && topic != null
                        && ((product.getTopic() != null && product.getTopic().equalsIgnoreCase(topic))
                                || (product.getName() != null
                                        && product.getName().equalsIgnoreCase(topic))))
                .flatMap(product -> {
                    List<ConsumerDTO> consumers = product.getConsumers();
                    return consumers == null ? Stream.empty() : consumers.stream();
                })
                .filter(consumer -> consumer != null
                        && consumer.getIdpClientId() != null
                        && consumer.getIdpClientId().equalsIgnoreCase(consumerId))
                .flatMap(consumer -> {
                    List<AttributesDTO> attrs = consumer.getAttributes();
                    return attrs == null ? Stream.empty() : attrs.stream();
                })
                .filter(Objects::nonNull)
                .toList();

        if (attributes.isEmpty()) {
            LOGGER.info("No filter attributes found for consumer '{}' on topic '{}'", consumerId, topic);
        } else {
            LOGGER.info(
                    "Found {} filter attribute(s) for consumer '{}' on topic '{}'",
                    attributes.size(),
                    consumerId,
                    topic);
        }
        return attributes;
    }

    /**
     * Determines whether a consumer has access to a given topic using the provided producer configuration.
     *
     * <p>Behavioral notes:
     * <ul>
     *   <li>Only the first producer in the configuration is considered (as per current business rule).</li>
     *   <li>Within that producer, products are filtered by an exact topic match.</li>
     *   <li>For matching products, consumers are checked for an idpClientId that matches the supplied consumerId (case-insensitive).</li>
     * </ul>
     *
     * <p>Nulls in the configuration or nested collections are handled defensively; if anything essential is missing, this method returns {@code false}.
     *
     * @param consumerId the consumer's IDP client id to check (case-insensitive)
     * @param topic the Kafka topic name to verify access for
     * @param producerConfigDTO the producer configuration source to inspect
     * @return {@code true} if a matching consumer is found for the topic under the first producer; otherwise {@code false}
     */
    private boolean hasConsumerAccessToTopic(String consumerId, String topic, ProducerConfigDTO producerConfigDTO) {
        if (producerConfigDTO == null || producerConfigDTO.getProducers() == null) {
            return false;
        }

        final String topicToMatch = topic == null ? null : topic.trim();

        return producerConfigDTO.getProducers().stream()
                .filter(Objects::nonNull)
                .flatMap(producer -> {
                    List<ProductDTO> products = producer.getProducts();
                    return products == null ? Stream.empty() : products.stream();
                })
                .filter(p -> p != null
                        && topicToMatch != null
                        && (p.getTopic() != null && p.getTopic().equalsIgnoreCase(topicToMatch)))
                .flatMap(p -> {
                    List<ConsumerDTO> consumers = p.getConsumers();
                    return consumers == null ? Stream.empty() : consumers.stream();
                })
                .anyMatch(c -> c != null
                        && c.getIdpClientId() != null
                        && c.getIdpClientId().equalsIgnoreCase(consumerId));
    }
}
