// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
 */
package uk.gov.dbt.ndtp.federator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.conductor.MessageConductor;
import uk.gov.dbt.ndtp.federator.conductor.RdfMessageConductor;
import uk.gov.dbt.ndtp.federator.consumer.ClientTopicOffsets;
import uk.gov.dbt.ndtp.federator.exceptions.AccessDeniedException;
import uk.gov.dbt.ndtp.federator.grpc.GRPCContextKeys;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.model.dto.AttributesDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProductDTO;
import uk.gov.dbt.ndtp.federator.utils.ProducerConsumerConfigServiceFactory;
import uk.gov.dbt.ndtp.federator.utils.ThreadUtil;
import uk.gov.dbt.ndtp.grpc.API;
import uk.gov.dbt.ndtp.grpc.APITopics;
import uk.gov.dbt.ndtp.grpc.TopicRequest;

/**
 * FederatorService is the main class that handles the processing of requests from the client.
 *
 * <p>It is responsible for:
 * <ul>
 *   <li>Verifying the client and key</li>
 *   <li>Returning the topics they can see</li>
 *   <li>Confirming that a topic requested is accessible by a client</li>
 *   <li>Confirming the data type held in a topic</li>
 *   <li>Getting data from a specific topic</li>
 *   <li>Handling the processing of messages</li>
 *   <li>Handling the completion of the request</li>
 *   <li>Handling the cancellation of the request</li>
 *   <li>Handling the shutdown of the request</li>
 * </ul>
 */
public class FederatorService {

    public static final Logger LOGGER = LoggerFactory.getLogger("FederatorService");
    private static final ExecutorService THREADED_EXECUTOR = ThreadUtil.threadExecutor("Federator");
    private final Set<String> sharedHeaders;

    /**
     * Creates a FederatorService with a KafkaEvent filter focused on Kafka headers.
     *
     * @param sharedHeaders are the header keys for headers to send to the client
     */
    public FederatorService(Set<String> sharedHeaders) {
        this.sharedHeaders = sharedHeaders;
    }

    /**
     * Takes an API request with the client name and key and returns topics they can see
     *
     * @param request for the caller
     * @return A response that outlines the topics the caller can see.
     * @throws AccessDeniedException if the client or key are invalid.
     */
    public APITopics getKafkaTopics(API request) throws AccessDeniedException {

        LOGGER.info("Started processing requests for client: {}", request.getClient());
        List<String> topicNames = obtainTopics(request.getClient(), request.getKey());
        LOGGER.debug("Obtained topics for {} :{}", request.getClient(), topicNames);
        return APITopics.newBuilder().addAllTopics(topicNames).build();
    }

    /**
     * Takes a request with the topic, client id, key, offset and the streamObservable object to write
     * into.
     *
     * @param request          that contains the details required to get data from a specific topic.
     * @param streamObservable used to write the data into.
     * @throws InvalidTopicException if the topic is not valid for a specific client.
     */
    public void getKafkaConsumer(TopicRequest request, StreamObservable streamObservable) throws InvalidTopicException {
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
        // From ManagementNode
        MessageConductor messageConductor =
                new RdfMessageConductor(topicData, streamObservable, filterAttributes, this.sharedHeaders);
        List<Future<?>> futures = new ArrayList<>();
        futures.add(THREADED_EXECUTOR.submit(messageConductor::processMessages));
        LOGGER.info("Awaiting consumer request finished.");
        ThreadUtil.awaitShutdown(futures, messageConductor, THREADED_EXECUTOR);
        LOGGER.info("Finished processing consumer request.");
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
                // Traverse all products that match the topic
                .flatMap(producer -> {
                    List<ProductDTO> products = producer.getProducts();
                    return products == null ? Stream.empty() : products.stream();
                })
                .filter(product -> product != null
                        && topic != null
                        && ((product.getTopic() != null && product.getTopic().equalsIgnoreCase(topic))
                                || (product.getName() != null
                                        && product.getName().equalsIgnoreCase(topic))))
                // From matching products, traverse consumers that match the idp client id
                .flatMap(product -> {
                    List<uk.gov.dbt.ndtp.federator.model.dto.ConsumerDTO> consumers = product.getConsumers();
                    return consumers == null ? Stream.empty() : consumers.stream();
                })
                .filter(consumer -> consumer != null
                        && consumer.getIdpClientId() != null
                        && consumer.getIdpClientId().equalsIgnoreCase(consumerId))
                // Collect their attributes
                .flatMap(consumer -> {
                    List<AttributesDTO> attrs = consumer.getAttributes();
                    return attrs == null ? Stream.empty() : attrs.stream();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

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

    private ProducerConfigDTO getProducerConfiguration() {
        return ProducerConsumerConfigServiceFactory.getProducerConsumerConfigService()
                .getProducerConfiguration();
    }

    /**
     * Verifies the client and key and returns the topics they can see.
     *
     * @param client the named account making the call.
     * @param key    the value used to identify the individual caller.
     * @return the list of topics that caller can access.
     */
    private List<String> obtainTopics(String client, String key) {
        LOGGER.info("Unimplemented: obtainTopics for client: {} with key {}", client, key);
        return Collections.emptyList();
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
