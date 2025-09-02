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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.SetParams;

/**
 * Utility class to interact with Redis.
 * <p>
 * This class is a singleton and should be used to interact with Redis.
 * It is used to set and get offsets for a given client and topic.
 * The class is thread-safe.
 * </p>
 */
public class RedisUtil {

    private final JedisPooled jedisPooled;

    private static RedisUtil instance;

    private static String redisAesKeyValue;

    public static final String REDIS_HOST = "redis.host";
    public static final String REDIS_PORT = "redis.port";
    public static final String REDIS_TLS_ENABLED = "redis.tls.enabled";
    public static final String REDIS_USERNAME = "redis.username";
    public static final String REDIS_PASSWORD = "redis.password";
    public static final String REDIS_AES_KEY = "redis.aes.key";

    public static final String LOCALHOST = "localhost";
    public static final String DEFAULT_PORT = "6379";
    public static final String TRUE = "true";
    public static final String TEST_CLIENT = "smoke_test_client";
    public static final String TEST_TOPIC = "smoke_test_topic";
    public static final long TEST_OFFSET = -150;

    public static final Logger LOGGER = LoggerFactory.getLogger("RedisUtil");

    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Gets the singleton instance of RedisUtil.
     *
     * @return the singleton instance of RedisUtil.
     */
    public static RedisUtil getInstance() {
        if (null == instance) {
            // Fetch configured AES key for encrypting/decrypting values stored in Redis
            redisAesKeyValue = PropertyUtil.getPropertyValue(REDIS_AES_KEY, "");

            String host = PropertyUtil.getPropertyValue(REDIS_HOST, LOCALHOST);
            LOGGER.info("Using Redis Host - '{}'", host);
            int port = PropertyUtil.getPropertyIntValue(REDIS_PORT, DEFAULT_PORT);
            LOGGER.info("Using Redis on Port - '{}'", port);
            boolean isTLSEnabled = PropertyUtil.getPropertyBooleanValue(REDIS_TLS_ENABLED, TRUE);
            LOGGER.info("Using TLS with Redis - '{}'", isTLSEnabled);

            String username = PropertyUtil.getPropertyValue(REDIS_USERNAME, "");
            String password = PropertyUtil.getPropertyValue(REDIS_PASSWORD, "");

            // Note redis authentication can be configured to use just a password, or both
            // username and password, hence
            // only presence of a password is tested
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
     *
     * @param jedisPooled Jedis pooled instance.
     */
    RedisUtil(JedisPooled jedisPooled) {
        this.jedisPooled = jedisPooled;
    }

    /**
     * Constructor without authentication.
     *
     * @param host         redis host address.
     * @param port         redis port number.
     * @param isTLSEnabled whether TLS is enabled.
     */
    private RedisUtil(String host, int port, boolean isTLSEnabled) {
        this(new JedisPooled(host, port, isTLSEnabled));
    }

    /**
     * Constructor with support for username and password authentication.
     *
     * @param host         redis host address.
     * @param port         redis port number.
     * @param isTLSEnabled whether TLS is enabled.
     * @param username     redis username.
     * @param password     redis password.
     */
    private RedisUtil(String host, int port, boolean isTLSEnabled, String username, String password) {
        this(buildAuthenticatedRedisConnection(host, port, isTLSEnabled, username, password));
    }

    /**
     * Builds a JedisPooled connection with support for username/password
     * authentication.
     *
     * @param host         redis host address.
     * @param port         redis port number.
     * @param isTLSEnabled whether TLS is enabled.
     * @param username     redis username.
     * @param password     redis password.
     */
    private static JedisPooled buildAuthenticatedRedisConnection(
            String host, int port, boolean isTLSEnabled, String username, String password) {

        // Create the JedisPooled instance with authentication. A Jedis client config
        // builder is used here in absence of
        // a native Jedis constructor
        // that supports both TLS and username/password authentication.
        DefaultJedisClientConfig.Builder jedisClientConfigBuilder =
                DefaultJedisClientConfig.builder().ssl(isTLSEnabled);

        if (!username.isBlank()) {
            jedisClientConfigBuilder.user(username);
        }

        if (!password.isBlank()) {
            jedisClientConfigBuilder.password(password);
        }

        return new JedisPooled(new HostAndPort(host, port), jedisClientConfigBuilder.build());
    }

    private long getOffset(String key) {
        key = qualifyOffset(key);
        LOGGER.debug("Retrieving offset from redis {}", key);
        String offset = getValue(key, String.class, redisAesKeyValueIsSet());
        return (offset != null ? Long.parseLong(offset) : 0L);
    }

    public long getOffset(String clientName, String topic) {
        return getOffset(clientName + "-" + topic);
    }

    private String setOffset(String key, long value) {
        key = qualifyOffset(key);
        LOGGER.debug("Persisting offset in redis {} = {}", key, value);
        setValue(key, value);
        return "OK";
    }

    public String setOffset(String clientName, String topic, long value) {
        return setOffset(clientName + "-" + topic, value);
    }

    private static String qualifyOffset(String key) {
        return "topic:" + key + ":offset";
    }

    /**
     * Stores a value in Redis at the given key without encryption.
     * No TTL.
     *
     * @param key   the Redis key
     * @param value the object to store
     * @param <T>   type of the value
     * @return true if Redis SET returned "OK"
     */
    public <T> boolean setValue(String key, T value) {
        return setValue(key, value, null);
    }

    /**
     * Stores a value in Redis at the given key with a TTL.
     * No encryption.
     *
     * @param key         the Redis key
     * @param value       the object to store
     * @param ttlSeconds  time-to-live in seconds; if null or &le; 0 then no TTL
     * @param <T>         type of the value
     * @return true if Redis SET returned "OK"
     */
    public <T> boolean setValue(String key, T value, Long ttlSeconds) {
        try {
            boolean encrypt = redisAesKeyValueIsSet();
            String json = MAPPER.writeValueAsString(value);
            String toWrite = encrypt ? AesCryptoUtil.encrypt(json, redisAesKeyValue) : json;

            if (ttlSeconds != null && ttlSeconds > 0) {
                LOGGER.debug("Persisting key in redis {} (encrypted={}, ttlSeconds={})", key, encrypt, ttlSeconds);
                return "OK"
                        .equals(jedisPooled.set(
                                key, toWrite, SetParams.setParams().ex(ttlSeconds)));
            } else {
                LOGGER.debug("Persisting key in redis {} (encrypted={}, no TTL)", key, encrypt);
                return "OK".equals(jedisPooled.set(key, toWrite));
            }
        } catch (Exception e) {
            throw new JedisDataException("Failed to set value in Redis for key " + key, e);
        }
    }

    /**
     * Retrieves a value from Redis at the given key without decryption.
     *
     * @param key  the Redis key
     * @param type the expected class type
     * @param <T>  type of the value
     * @param encrypted  whether the value will have been encrypted
     * @return the deserialised object, or null if the key is not found
     */
    public <T> T getValue(String key, Class<T> type, boolean encrypted) {
        try {
            String stored = jedisPooled.get(key);
            if (stored == null) return null;
            String json =
                    encrypted && redisAesKeyValueIsSet() ? AesCryptoUtil.decrypt(stored, redisAesKeyValue) : stored;
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new JedisDataException("Failed to get value from Redis for key " + key, e);
        }
    }

    /**
     * Checks if an AES key for encrypting/decrypting values in Redis has been set.
     * @return true if an AES key has been set, otherwise false
     */
    private static boolean redisAesKeyValueIsSet() {
        return redisAesKeyValue != null && !redisAesKeyValue.isBlank();
    }
}
