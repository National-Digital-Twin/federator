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
import org.apache.kafka.common.errors.InvalidTopicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.access.AccessMap;
import uk.gov.dbt.ndtp.federator.access.mappings.AccessDetails;
import uk.gov.dbt.ndtp.federator.conductor.MessageConductor;
import uk.gov.dbt.ndtp.federator.conductor.RdfMessageConductor;
import uk.gov.dbt.ndtp.federator.consumer.ClientTopicOffsets;
import uk.gov.dbt.ndtp.federator.exceptions.AccessDeniedException;
import uk.gov.dbt.ndtp.federator.grpc.GRPCContextKeys;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
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
     * @param filterList    (List of - client: message filters) to make decisions on client federation
     *                      of data
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
        String clientId = GRPCContextKeys.CLIENT_ID.get();
        streamObservable.setOnCancelHandler(() -> LOGGER.error("Cancel called by client: {}", clientId));

        if (!hasClientAccessToTopic(clientId, topic)) {
            String errMsg = String.format("Topic (%s) is not valid for client (%s).", topic, clientId);
            LOGGER.error(errMsg);
            throw new InvalidTopicException(errMsg);
        }
        ClientTopicOffsets topicData = new ClientTopicOffsets(clientId, topic, offset);
        MessageConductor messageConductor = new RdfMessageConductor(topicData, streamObservable, this.sharedHeaders);
        List<Future<?>> futures = new ArrayList<>();
        futures.add(THREADED_EXECUTOR.submit(messageConductor::processMessages));
        LOGGER.info("Awaiting consumer request finished.");
        ThreadUtil.awaitShutdown(futures, messageConductor, THREADED_EXECUTOR);
        LOGGER.info("Finished processing consumer request.");
        streamObservable.onCompleted();
    }

    /**
     * Verifies the client and key and returns the topics they can see.
     *
     * @param client the named account making the call.
     * @param key    the value used to identify the individual caller.
     * @return the list of topics that caller can access.
     */
    private List<String> obtainTopics(String client, String key) {
        AccessMap.get().verifyDetails(client, key);
        AccessDetails details = AccessMap.get().getDetails(client);
        String message = String.format("Client (%s) topics to federate from - %s", client, details.getTopicNames());
        LOGGER.info(message);
        return (null != details) ? details.getTopicNames() : Collections.emptyList();
    }

    /**
     * Checks if a client has access to a specific topic., by getting Consumer Configuration
     * and filtering the producers by the client id, then mapping the data providers to a set
     * of topics.
     */
    private boolean hasClientAccessToTopic(String clientId, String topic) {
        ConsumerConfigDTO consumerConfiguration =
                ProducerConsumerConfigServiceFactory.getProducerConsumerConfigService()
                        .getConsumerConfiguration();

        Set<String> validTopics = consumerConfiguration.getProducers().stream()
                .filter(producer -> clientId.equalsIgnoreCase(producer.getIdpClientId()))
                .map(producer -> producer.getDataProviders().stream()
                        .map(ProductDTO::getTopic)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        return validTopics.contains(topic);
    }
}
