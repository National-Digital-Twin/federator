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

package uk.gov.dbt.ndtp.federator.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Utility class to interact with Redis.
 * <p>
 *   This class is a singleton and should be used to interact with Redis.
 *   It is used to set and get offsets for a given client and topic.
 *   The class is thread-safe.
 * </p>
 */
public class RedisUtil {

    private final JedisPooled jedisPooled;

    private static RedisUtil instance;

    public static final String REDIS_HOST = "redis.host";
    public static final String REDIS_PORT = "redis.port";
    public static final String REDIS_TLS_ENABLED = "redis.tls.enabled";
    public static final String LOCALHOST = "localhost";
    public static final String DEFAULT_PORT = "6379";
    public static final String TRUE = "true";
    public static final String TEST_CLIENT = "smoke_test_client";
    public static final String TEST_TOPIC = "smoke_test_topic";
    public static final long TEST_OFFSET = -150;

    public static final Logger LOGGER = LoggerFactory.getLogger("RedisUtil");

    public static RedisUtil getInstance() {
        if (null == instance) {
            String host = PropertyUtil.getPropertyValue(REDIS_HOST, LOCALHOST);
            LOGGER.info("Using Redis Host - '{}'", host);
            int port = PropertyUtil.getPropertyIntValue(REDIS_PORT, DEFAULT_PORT);
            LOGGER.info("Using Redis on Port - '{}'", port);
            boolean isTLSEnabled = PropertyUtil.getPropertyBooleanValue(REDIS_TLS_ENABLED, TRUE);
            LOGGER.info("Using TLS with Redis - '{}'", isTLSEnabled);
            instance = new RedisUtil(host, port, isTLSEnabled);
            try {
                String ok = instance.setOffset(TEST_CLIENT, TEST_TOPIC, TEST_OFFSET);
                LOGGER.info("Set test data and got {}", ok);
                long value = instance.getOffset(TEST_CLIENT, TEST_TOPIC);
                LOGGER.info("Got test data - {}", value);
                if (value != TEST_OFFSET) {
                    LOGGER.error("Values don't match throwing exception");
                    throw new RuntimeException("REDIS failed to return the correct smoke test offset");
                }
            } catch (JedisConnectionException e) {
                String errMsg = String.format(
                        "Failed to connect to REDIS to run 'Smoke Test' queries. Error message - '%s'", e.getMessage());
                LOGGER.error(errMsg);
                LOGGER.error("Calling REDIS on '{}:{}' using TLS '{}'", host, port, isTLSEnabled);
                throw new JedisConnectionException(errMsg, e);
            }
        }
        return instance;
    }

    private RedisUtil(String host, int port, boolean isTLSEnabled) {
        this(new JedisPooled(host, port, isTLSEnabled));
    }

    RedisUtil(JedisPooled jedisPooled) {
        this.jedisPooled = jedisPooled;
    }

    private long getOffset(String key) {
        key = qualifyOffset(key);
        LOGGER.debug("Retrieving offset from redis {}", key);
        String offset = jedisPooled.get(key);
        return (offset != null ? Long.parseLong(offset) : 0L);
    }

    public long getOffset(String clientName, String topic) {
        return getOffset(clientName + "-" + topic);
    }

    private String setOffset(String key, long value) {
        key = qualifyOffset(key);
        LOGGER.debug("Persisting offset in redis {} = {}", key, value);
        return jedisPooled.set(key, String.valueOf(value));
    }

    public String setOffset(String clientName, String topic, long value) {
        return setOffset(clientName + "-" + topic, value);
    }

    private static String qualifyOffset(String key) {
        return "topic:" + key + ":offset";
    }
}
