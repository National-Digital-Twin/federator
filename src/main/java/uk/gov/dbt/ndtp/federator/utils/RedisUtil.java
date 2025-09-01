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
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
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
    public static final String REDIS_USERNAME = "redis.username";
    public static final String REDIS_PASSWORD = "redis.password";

    public static final String LOCALHOST = "localhost";
    public static final String DEFAULT_PORT = "6379";
    public static final String TRUE = "true";
    public static final String TEST_CLIENT = "smoke_test_client";
    public static final String TEST_TOPIC = "smoke_test_topic";
    public static final long TEST_OFFSET = -150;

    public static final Logger LOGGER = LoggerFactory.getLogger("RedisUtil");

    /**
     * Gets the singleton instance of RedisUtil.
     * @return the singleton instance of RedisUtil.
     */
    public static RedisUtil getInstance() {
        if (null == instance) {
            String host = PropertyUtil.getPropertyValue(REDIS_HOST, LOCALHOST);
            LOGGER.info("Using Redis Host - '{}'", host);
            int port = PropertyUtil.getPropertyIntValue(REDIS_PORT, DEFAULT_PORT);
            LOGGER.info("Using Redis on Port - '{}'", port);
            boolean isTLSEnabled = PropertyUtil.getPropertyBooleanValue(REDIS_TLS_ENABLED, TRUE);
            LOGGER.info("Using TLS with Redis - '{}'", isTLSEnabled);

            String username = PropertyUtil.getPropertyValue(REDIS_USERNAME, "");
            String password = PropertyUtil.getPropertyValue(REDIS_PASSWORD, "");

            // Note redis authentication can be configured to use just a password, or both username and password, hence only presence of a password is tested
            // to determine whether authentication credentials should be used.
            if (!password.isBlank()) {
                LOGGER.info("Using authentication with Redis");  
                instance = new RedisUtil(host, port, isTLSEnabled, username, password);         
            } else {
                instance = new RedisUtil(host, port, isTLSEnabled);
            }

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

    /**
     * Constructor with existing Jedis pooled instance.
     * @param jedisPooled Jedis pooled instance.
     */
    RedisUtil(JedisPooled jedisPooled) {
        this.jedisPooled = jedisPooled;
    }

    /**
     * Constructor without authentication.
     * @param host redis host address.
     * @param port redis port number.
     * @param isTLSEnabled whether TLS is enabled.
     */
    private RedisUtil(String host, int port, boolean isTLSEnabled) {
        this(new JedisPooled(host, port, isTLSEnabled));
    }

    /**
     * Constructor with support for username and password authentication.
     * @param host redis host address.
     * @param port redis port number.
     * @param isTLSEnabled whether TLS is enabled.
     * @param username redis username.
     * @param password redis password.
     */
    private RedisUtil(String host, int port, boolean isTLSEnabled, String username, String password) {
        this(buildAuthenticatedRedisConnection(host, port, isTLSEnabled, username, password));
    }

    /**
     * Builds a JedisPooled connection with support for username/password authentication.
     * @param host redis host address.
     * @param port redis port number.
     * @param isTLSEnabled whether TLS is enabled.
     * @param username redis username.
     * @param password redis password.
     */
    private static JedisPooled buildAuthenticatedRedisConnection(String host, int port, boolean isTLSEnabled, String username, String password) {

        // Create the JedisPooled instance with authentication. A Jedis client config builder is used here in absence of a native Jedis constructor
        // that supports both TLS and username/password authentication.
        DefaultJedisClientConfig.Builder b = DefaultJedisClientConfig.builder().ssl(isTLSEnabled);

        if (!username.isBlank()) {
            b.user(username);
        };

        if (!password.isBlank()) { 
            b.password(password);
        }

        return new JedisPooled(new HostAndPort(host, port), b.build());
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
