package uk.gov.dbt.ndtp.federator.common.service.stream;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import uk.gov.dbt.ndtp.federator.common.model.dto.AttributesDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ConsumerDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProductDTO;
import uk.gov.dbt.ndtp.federator.common.utils.ProducerConsumerConfigServiceFactory;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;

public interface FederatorStreamService<R, T> {
    Logger LOGGER = org.slf4j.LoggerFactory.getLogger(FederatorStreamService.class);

    void streamToClient(R request, StreamObservable<T> streamObservable);

    default ProducerConfigDTO getProducerConfiguration() {
        return ProducerConsumerConfigServiceFactory.getProducerConfigService().getProducerConfiguration();
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
    default List<AttributesDTO> getFilterAttributesForConsumer(
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
}
