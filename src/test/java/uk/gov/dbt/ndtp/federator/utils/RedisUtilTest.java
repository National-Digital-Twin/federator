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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redis.testcontainers.RedisContainer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import redis.clients.jedis.JedisPooled;

@Testcontainers
class RedisUtilTest {

    @Container
    private static final RedisContainer redis = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME);

    private final long RANDOM_OFFSET = new Random().nextLong();
    private final String RANDOM_CLIENT = RandomStringUtils.random(6, true, true);
    private final String RANDOM_TOPIC = RandomStringUtils.random(6, true, false);

    private JedisPooled pool;
    private RedisUtil underTest;

    @BeforeEach
    void setUp() {
        pool = new JedisPooled(redis.getRedisHost(), redis.getRedisPort());
        underTest = new RedisUtil(pool);
    }

    @AfterEach
    void tearDown() {
        pool.close();
    }

    @Test
    void test_noEntry_returns_0L() {
        // given
        // when
        long actual = underTest.getOffset("a", "missingKey");
        // then
        assertEquals(0L, actual);
    }

    @Test
    void test_matchingEntryOfParts_returns() {
        // given
        pool.set("topic:" + RANDOM_CLIENT + "-" + RANDOM_TOPIC + ":offset", String.valueOf(RANDOM_OFFSET));
        // when
        long actual = underTest.getOffset(RANDOM_CLIENT, RANDOM_TOPIC);
        // then
        assertEquals(RANDOM_OFFSET, actual);
    }

    @Test
    void test_setEntryOfParts() {
        // given
        // when
        underTest.setOffset(RANDOM_CLIENT, RANDOM_TOPIC, RANDOM_OFFSET);
        // then
        String stored = pool.get("topic:" + RANDOM_CLIENT + "-" + RANDOM_TOPIC + ":offset");
        assertEquals(String.valueOf(RANDOM_OFFSET), stored);
    }

    @Nested
    class Initialising {
        @BeforeAll
        static void beforeAll() throws IOException {
            TestPropertyUtil.clearProperties();
            Path tmp = Files.createTempFile(null, null);

            Files.writeString(
                    tmp,
                    """
                            redis.host=%s
                            redis.port=%d
                            redis.tls.enabled=false"""
                            .formatted(redis.getRedisHost(), redis.getRedisPort()));

            File tmpProperties = tmp.toFile();
            tmpProperties.deleteOnExit();

            PropertyUtil.init(tmpProperties);
        }

        @AfterAll
        static void afterAll() {
            TestPropertyUtil.clearProperties();
        }

        @Test
        void getInstance_withSmokeTest() {
            RedisUtil instance = RedisUtil.getInstance();

            assertNotNull(instance);

            String smokeTestValue = pool.get("topic:smoke_test_client-smoke_test_topic:offset");
            assertEquals("-150", smokeTestValue);
        }
    }

    @Nested
    class InitialisingWithAuth {

        private static final String USER = "app_user";
        private static final String PASS = "secret_pass";

        @BeforeAll
        static void beforeAll() throws Exception {
            TestPropertyUtil.clearProperties();

            // create ACL user
            try (redis.clients.jedis.Jedis admin = new redis.clients.jedis.Jedis(redis.getRedisHost(),
                    redis.getRedisPort())) {
                admin.aclSetUser(USER, "on", ">" + PASS, "allcommands", "allkeys");
            }

            // write props to use username/password path
            Path tmp = Files.createTempFile(null, null);
            Files.writeString(
                    tmp,
                    """
                            redis.host=%s
                            redis.port=%d
                            redis.tls.enabled=false
                            redis.username=%s
                            redis.password=%s
                            """.formatted(redis.getRedisHost(), redis.getRedisPort(), USER, PASS));
            File tmpProperties = tmp.toFile();
            tmpProperties.deleteOnExit();
            PropertyUtil.init(tmpProperties);

            // reset the singleton
            var f = RedisUtil.class.getDeclaredField("instance");
            f.setAccessible(true);
            f.set(null, null);
        }

        @AfterAll
        static void afterAll() throws Exception {
            // clean up ACL user
            try (redis.clients.jedis.Jedis admin = new redis.clients.jedis.Jedis(redis.getRedisHost(),
                    redis.getRedisPort())) {
                admin.aclDelUser(USER);
            }
            TestPropertyUtil.clearProperties();
            var f = RedisUtil.class.getDeclaredField("instance");
            f.setAccessible(true);
            f.set(null, null);
        }

        @Test
        void getInstance_withSmokeTest_andAuth() {
            RedisUtil instance = RedisUtil.getInstance();

            assertNotNull(instance);

            String smokeTestValue = pool.get("topic:smoke_test_client-smoke_test_topic:offset");
            assertEquals("-150", smokeTestValue);
        }
    }
}
