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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.exceptions.JedisConnectionException;
import uk.gov.dbt.ndtp.federator.exceptions.RetryableException;
import uk.gov.dbt.ndtp.federator.interfaces.KafkaConsumable;
import uk.gov.dbt.ndtp.federator.lifecycle.ProgressReporter;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.utils.RedisUtil;
import uk.gov.dbt.ndtp.federator.utils.ThreadUtil;

/**
 * Simple class that runs the basic logic to check for topic info, and process
 * data based on the topic, offset stored locally (REDIS) by client topic key.
 */
public class KafkaRunner {

    public static final String RETRIES_MAX_ATTEMPTS = "retries.max_attempts";
    public static final String RETRIES_INITIAL_BACKOFF = "retries.initial_backoff";
    public static final String RETRIES_MAX_BACKOFF = "retries.max_backoff";
    public static final String RETRIES_FOREVER = "retries.forever";

    public static final int MAX_RETRIES = PropertyUtil.getPropertyIntValue(RETRIES_MAX_ATTEMPTS, "200");
    public static final long INITIAL_BACKOFF_TIME_MS =
            PropertyUtil.getPropertyLongValue(RETRIES_INITIAL_BACKOFF, "500");
    public static final long MAX_BACK_OFF_MS = PropertyUtil.getPropertyLongValue(RETRIES_MAX_BACKOFF, "60000");
    public static final boolean CHECK_FOREVER = PropertyUtil.getPropertyBooleanValue(RETRIES_FOREVER, "true");

    public static final Logger LOGGER = LoggerFactory.getLogger("KafkaRunner");

    private static final ExecutorService THREADED_EXECUTOR = ThreadUtil.threadExecutor("Client");
    private long backOffTimeMs = INITIAL_BACKOFF_TIME_MS;

    private final ProgressReporter reporter;

    public KafkaRunner() {
        this(ProgressReporter.NOOP_PROGRESS_REPORTER);
    }

    public KafkaRunner(ProgressReporter reporter) {
        this.reporter = reporter;
    }

    /**
     * Takes a KafkaConsumable and then runs the export using the client information
     * and topic listing, processing functionality.
     *
     * @param consumer does the topic listing and processing heavy lift.
     * @throws InterruptedException if the system fails to sleep as it waits for
     *                              data.
     */
    public void run(KafkaConsumable consumer) throws InterruptedException {
        LOGGER.info("run called....");
        for (int i = 0; i < MAX_RETRIES; ++i) {
            LOGGER.info("run called for the '{}' / '{}'", (i + 1), MAX_RETRIES);
            try {
                List<String> topics = getTopics(consumer);
                processTopics(consumer, topics);
                // Successful process run so reset backOffTimeMs to the initial short value
                backOffTimeMs = INITIAL_BACKOFF_TIME_MS;
                if (!CHECK_FOREVER) {
                    LOGGER.info("Finished processing");
                    break;
                }
            } catch (RetryableException e) {
                backOffTimeMs = getAndUpdateBackOffTimeInMs(backOffTimeMs);
                LOGGER.error("Error encountered: '{}'. Retrying in '{}' ms", e.getMessage(), backOffTimeMs);
                LOGGER.debug("Exception encountered:", e);
                Thread.sleep(backOffTimeMs);
            }
        }
        reporter.registerComplete();
    }

    private List<String> getTopics(KafkaConsumable consumer) {
        return consumer.obtainTopics();
    }

    /**
     * Process the topics from the list of topics.
     * @param consumer a consumer that implements the KafkaConsumable interface (e.g. WrappedGRPCClient)
     * @param topics a list of topics to process
     */
    private void processTopics(KafkaConsumable consumer, List<String> topics) {
        List<Future<?>> futures = new ArrayList<>();
        String prefix = consumer.getRedisPrefix();
        try {
            for (String topic : topics) {
                long offset = RedisUtil.getInstance().getOffset(prefix, topic);
                LOGGER.info("Processing topic: '{}' with offset: '{}'", topic, offset);
                // Smoke test to see if the thing we are writing too exists
                consumer.testConnectivity();
                Runnable task = () -> consumer.processTopic(topic, offset);
                futures.add(THREADED_EXECUTOR.submit(task));
            }
            ThreadUtil.awaitShutdown(futures, consumer, THREADED_EXECUTOR);
        } catch (JedisConnectionException e) {
            LOGGER.error("Failure connecting to Redis: '{}'", e.getMessage());
            LOGGER.debug("Exception occurred connecting to Redis:", e);
            throw new RetryableException(e);
        } catch (KafkaException e) {
            LOGGER.error("Failure connecting to Kafka: '{}'", e.getMessage());
            LOGGER.debug("Exception occurred connecting to Kafka:", e);
            throw new RetryableException(e);
        } catch (Exception e) {
            LOGGER.error("Encountered exception during topic processing", e);
        }
    }

    /**
     * Get the back off time in milliseconds and update the back off time. Used within the main run()
     * method to determine how long to wait before retrying.
     *
     * @param bOffTimeMs the current back off time in milliseconds
     * @return long back off time in milliseconds.
     */
    private long getAndUpdateBackOffTimeInMs(long bOffTimeMs) {
        if (bOffTimeMs < KafkaRunner.MAX_BACK_OFF_MS) {
            bOffTimeMs += bOffTimeMs;
        } else {
            bOffTimeMs = KafkaRunner.MAX_BACK_OFF_MS;
        }
        return bOffTimeMs;
    }
}
