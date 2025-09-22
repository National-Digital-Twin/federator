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

    public static final String REDIS_HOST = "redis.host";
    public static final String REDIS_PORT = "redis.port";
    public static final String REDIS_KEY_PREFIX = "redis.prefix";
    public static final String REDIS_TLS_ENABLED = "redis.tls.enabled";
    public static final String REDIS_USERNAME = "redis.username";
    public static final String REDIS_PASSWORD = "redis.password";
    public static final String REDIS_AES_KEY = "redis.aes.key";
    public static final String LOCALHOST = "localhost";
    public static final String DEFAULT_PORT = "6379";
    public static final String TRUE = "true";
    public static final Logger LOGGER = LoggerFactory.getLogger("RedisUtil");
    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static RedisUtil instance;
    private static String redisAesKeyValue;
    private final JedisPooled jedisPooled;

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
     * Gets the singleton instance of RedisUtil.
     *
     * @return the singleton instance of RedisUtil.
     */
    public static synchronized RedisUtil getInstance() {
        if (instance == null) {
            redisAesKeyValue = PropertyUtil.getPropertyValue(REDIS_AES_KEY, "");
            String host = PropertyUtil.getPropertyValue(REDIS_HOST, LOCALHOST);
            LOGGER.info("Using Redis Host - '{}'", host);
            int port = PropertyUtil.getPropertyIntValue(REDIS_PORT, DEFAULT_PORT);
            LOGGER.info("Using Redis on Port - '{}'", port);
            boolean isTLSEnabled = PropertyUtil.getPropertyBooleanValue(REDIS_TLS_ENABLED, TRUE);
            LOGGER.info("Using TLS with Redis - '{}'", isTLSEnabled);

            String username = PropertyUtil.getPropertyValue(REDIS_USERNAME, "");
            String password = PropertyUtil.getPropertyValue(REDIS_PASSWORD, "");

            if (!password.isBlank()) {
                LOGGER.info("Using authentication with Redis");
                instance = new RedisUtil(host, port, isTLSEnabled, username, password);
            } else {
                instance = new RedisUtil(host, port, isTLSEnabled);
            }
        }
        return instance;
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

    private static String qualifyOffset(String key) {
        key = getPrefixedKey(key);
        return "topic:" + key + ":offset";
    }

    /**
     * Checks if an AES key for encrypting/decrypting values in Redis has been set.
     * @return true if an AES key has been set, otherwise false
     */
    private static boolean redisAesKeyValueIsSet() {
        return redisAesKeyValue != null && !redisAesKeyValue.isBlank();
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
        return setValue(getPrefixedKey(key), value, null);
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
            key = getPrefixedKey(key);
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
            key = getPrefixedKey(key);
            String stored = jedisPooled.get(key);
            if (stored == null) return null;
            String json =
                    encrypted && redisAesKeyValueIsSet() ? AesCryptoUtil.decrypt(stored, redisAesKeyValue) : stored;
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new JedisDataException("Failed to get value from Redis for key " + key, e);
        }
    }

    private static String getPrefixedKey(String key) {
        String prefix;
        try {
            prefix = PropertyUtil.getPropertyValue(REDIS_KEY_PREFIX, "");
        } catch (PropertyUtil.PropertyUtilException e) {
            // PropertyUtil not initialised in some tests/contexts; default to no prefix
            prefix = "";
        }
        if (prefix == null || prefix.isBlank()) {
            return key;
        }
        return prefix + ":" + key;
    }
}
