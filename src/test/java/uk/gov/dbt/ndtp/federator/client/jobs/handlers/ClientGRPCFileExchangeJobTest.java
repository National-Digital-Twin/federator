/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.client.jobs.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.client.grpc.GRPCFileClient;
import uk.gov.dbt.ndtp.federator.client.jobs.params.ClientFileExchangeGRPCJobParams;
import uk.gov.dbt.ndtp.federator.client.jobs.params.FileExchangeProperties;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.RedisUtil;
import uk.gov.dbt.ndtp.federator.exceptions.ClientGRPCJobException;

class ClientGRPCFileExchangeJobTest {

    private MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    private MockedStatic<RedisUtil> redisUtilMockedStatic;
    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        propertyUtilMockedStatic = mockStatic(PropertyUtil.class);
        redisUtilMockedStatic = mockStatic(RedisUtil.class);
        redisUtil = mock(RedisUtil.class);
        redisUtilMockedStatic.when(RedisUtil::getInstance).thenReturn(redisUtil);
        propertyUtilMockedStatic
                .when(() -> PropertyUtil.getPropertyValue(anyString(), anyString()))
                .thenReturn("prefix");
    }

    @AfterEach
    void tearDown() {
        propertyUtilMockedStatic.close();
        redisUtilMockedStatic.close();
    }

    @Test
    void testRun_success() {
        ConnectionProperties conn = mock(ConnectionProperties.class);
        FileExchangeProperties fep = FileExchangeProperties.builder()
                .sourcePath("src")
                .destinationPath("dest")
                .build();
        ClientFileExchangeGRPCJobParams params = new ClientFileExchangeGRPCJobParams(fep, "topic", conn, "node-1");

        when(redisUtil.getOffset(anyString(), eq("topic"))).thenReturn(10L);

        try (MockedConstruction<GRPCFileClient> mocked = mockConstruction(GRPCFileClient.class, (mock, context) -> {
            when(mock.getRedisPrefix()).thenReturn("redis-prefix");
        })) {
            ClientGRPCFileExchangeJob job = new ClientGRPCFileExchangeJob(params);
            job.run(params);

            GRPCFileClient mockClient = mocked.constructed().get(0);
            verify(mockClient).processTopic("topic", 10L, "dest");
        }
    }

    @Test
    void testRun_missingDestination() {
        ConnectionProperties conn = mock(ConnectionProperties.class);
        FileExchangeProperties fep = FileExchangeProperties.builder()
                .sourcePath("src")
                .destinationPath("")
                .build();
        ClientFileExchangeGRPCJobParams params = new ClientFileExchangeGRPCJobParams(fep, "topic", conn, "node-1");

        ClientGRPCFileExchangeJob job = new ClientGRPCFileExchangeJob(params);
        assertThrows(ClientGRPCJobException.class, () -> job.run(params));
    }

    @Test
    void testRun_exceptionInClient() {
        ConnectionProperties conn = mock(ConnectionProperties.class);
        FileExchangeProperties fep = FileExchangeProperties.builder()
                .sourcePath("src")
                .destinationPath("dest")
                .build();
        ClientFileExchangeGRPCJobParams params = new ClientFileExchangeGRPCJobParams(fep, "topic", conn, "node-1");

        try (MockedConstruction<GRPCFileClient> mocked = mockConstruction(GRPCFileClient.class, (mock, context) -> {
            doThrow(new RuntimeException("error")).when(mock).processTopic(anyString(), anyLong(), anyString());
        })) {
            ClientGRPCFileExchangeJob job = new ClientGRPCFileExchangeJob(params);
            assertThrows(ClientGRPCJobException.class, () -> job.run(params));
        }
    }

    @Test
    void testToString() {
        ClientGRPCFileExchangeJob job = new ClientGRPCFileExchangeJob();
        assertEquals("Client File Exchange Over GRPC Job", job.toString());
    }
}
