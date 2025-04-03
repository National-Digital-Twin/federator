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
import java.util.Map;
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
import uk.gov.dbt.ndtp.federator.access.mappings.AccessTopics;
import uk.gov.dbt.ndtp.federator.conductor.MessageConductor;
import uk.gov.dbt.ndtp.federator.conductor.RdfMessageConductor;
import uk.gov.dbt.ndtp.federator.consumer.ClientTopicOffsets;
import uk.gov.dbt.ndtp.federator.exceptions.AccessDeniedException;
import uk.gov.dbt.ndtp.federator.filter.MessageFilter;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.utils.ClientFilter;
import uk.gov.dbt.ndtp.federator.utils.ThreadUtil;
import uk.gov.dbt.ndtp.grpc.API;
import uk.gov.dbt.ndtp.grpc.APITopics;
import uk.gov.dbt.ndtp.grpc.TopicRequest;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

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
    private static final String RDF_DATA_TYPE = "RDF";
    private static final String NOT_SET = "NOT_SET";
    private final Map<String, MessageFilter<KafkaEvent<?, ?>>> filterMap;
    private final Set<String> sharedHeaders;

    /**
     * Creates a FederatorService with a KafkaEvent filter focused on Kafka headers.
     *
     * @param filterList    (List of - client: message filters) to make decisions on client federation
     *                      of data
     * @param sharedHeaders are the header keys for headers to send to the client
     */
    public FederatorService(List<ClientFilter> filterList, Set<String> sharedHeaders) {
        this.sharedHeaders = sharedHeaders;
        filterMap = filterList.stream()
                .collect(Collectors.toMap(
                        ClientFilter::clientId, // key mapper
                        ClientFilter::messageFilter // value mapper
                        ));
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
     * Confirms that a topic requested is accessable by a client.
     *
     * @param client the named account making the call.
     * @param key    the value used to identify the individual caller.
     * @param topic  the topic being requested.
     * @return true if the client has access to that topic.
     */
    private boolean isTopicValid(String client, String key, String topic) {
        return obtainTopics(client, key).contains(topic);
    }

    /**
     * Confirms the data type held in a topic.
     * <p>
     * The aim is to allow expansion to process beyond RDF. Future proofing the architecture.
     *
     * @param client the named account making the call.
     * @param topic  the topic being requested.
     * @return data type held in topic
     */
    private String getTopicContent(String client, String topic) {
        AccessDetails details = AccessMap.get().getDetails(client);
        List<AccessTopics> list = details.getTopics();
        for (AccessTopics accessTopics : list) {
            String topicName = accessTopics.getName();
            if (topicName.equals(topic)) {
                // The data type held should be added to Access topic message.
                return RDF_DATA_TYPE;
            }
        }
        return NOT_SET;
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
        String client = request.getClient();
        String aPIKey = request.getAPIKey();
        long offset = request.getOffset();
        streamObservable.setOnCancelHandler(() -> LOGGER.error("Cancel called by client: {}", client));
        if (!isTopicValid(client, aPIKey, topic)) {
            String errMsg = String.format("Topic (%s) is not valid for client (%s).", topic, client);
            LOGGER.error(errMsg);
            throw new InvalidTopicException(errMsg);
        }
        ClientTopicOffsets topicData = new ClientTopicOffsets(client, topic, offset);
        String topicDataType = this.getTopicContent(client, topic);
        MessageConductor messageConductor;
        MessageFilter<KafkaEvent<?, ?>> eventMessageFilter = filterMap.get(client);
        if (Objects.isNull(eventMessageFilter)) {
            throw new IllegalStateException("No filter found for client: " + client);
        }

        // Pick out MessageConductors that work with other data types (JSON, raw etc)
        if (topicDataType.equals(RDF_DATA_TYPE)) {
            messageConductor =
                    new RdfMessageConductor(topicData, streamObservable, eventMessageFilter, this.sharedHeaders);
        } else if (topicDataType.equals(NOT_SET)) {
            String errMsg = String.format(
                    "Could not get topic (%s) data type for client (%s), returned NOT SET.", topic, client);
            LOGGER.error(errMsg);
            throw new InvalidTopicException(errMsg);
        } else {
            String errMsg = String.format(
                    "Topic (%s) has an unknown data type recorded (%s) for client (%s).", topicDataType, topic, client);
            LOGGER.error(errMsg);
            throw new InvalidTopicException(errMsg);
        }
        List<Future<?>> futures = new ArrayList<>();
        futures.add(THREADED_EXECUTOR.submit(messageConductor::processMessages));
        LOGGER.info("Awaiting consumer request finished.");
        ThreadUtil.awaitShutdown(futures, messageConductor, THREADED_EXECUTOR);
        LOGGER.info("Finished processing consumer request.");
        streamObservable.onCompleted();
    }
}
