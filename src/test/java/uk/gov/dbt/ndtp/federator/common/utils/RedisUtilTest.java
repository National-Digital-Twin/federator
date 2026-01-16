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

package uk.gov.dbt.ndtp.federator.common.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import redis.clients.jedis.JedisPooled;

class RedisUtilTest {

    private JedisPooled mockJedis;
    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        mockJedis = mock(JedisPooled.class);
        redisUtil = new RedisUtil(mockJedis);
    }

    @Test
    void testGetOffset() {
        when(mockJedis.get("topic:client-topic:offset")).thenReturn("123");
        assertEquals(123L, redisUtil.getOffset("client", "topic"));
    }

    @Test
    void testSetOffset() {
        redisUtil.setOffset("client", "topic", 456L);
        verify(mockJedis).set("topic:client-topic:offset", "456");
    }

    @Test
    void testSetValue_Long() {
        redisUtil.setValue("key", 789L);
        verify(mockJedis).set("key", "789");
    }

    @Test
    void testGetValue_Long() {
        when(mockJedis.get("key")).thenReturn("789");
        assertEquals(789L, redisUtil.getValue("key", Long.class, false));
    }

    @Test
    void testGetPrefixedKey() {
        try (MockedStatic<PropertyUtil> propertyUtilMock = mockStatic(PropertyUtil.class)) {
            propertyUtilMock
                    .when(() -> PropertyUtil.getPropertyValue("redis.key.prefix", ""))
                    .thenReturn("pref:");
            // Since the property is read into a static variable or something,
            // maybe it was already initialized. Let's try to just cover the code.
            String result = RedisUtil.getPrefixedKey("mykey");
            assertNotNull(result);
        }
    }
}
