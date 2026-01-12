/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.client.jobs.params;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;

class JobParamsTest {

    @Test
    void testJobParamsBuilderAndGetters() {
        ConnectionProperties conn = new ConnectionProperties("c1", "k1", "s1", "localhost", 8080, false);
        JobParams params = JobParams.builder()
                .jobId("id1")
                .jobName("name1")
                .managementNodeId("mn1")
                .amountOfRetries(3)
                .jobScheduleType("cron")
                .scheduleExpression("* * *")
                .requireImmediateTrigger(true)
                .connectionProperties(conn)
                .build();

        assertEquals("id1", params.getJobId());
        assertEquals("name1", params.getJobName());
        assertEquals("mn1", params.getManagementNodeId());
        assertEquals(3, params.getAmountOfRetries());
        assertEquals("cron", params.getJobScheduleType());
        assertEquals("* * *", params.getScheduleExpression());
        assertTrue(params.getRequireImmediateTrigger());
        assertEquals(conn, params.getConnectionProperties());
        assertEquals("name1", params.toString());
    }

    @Test
    void testJobParamsEqualsAndHashCode() {
        ConnectionProperties conn = new ConnectionProperties("c1", "k1", "s1", "localhost", 8080, false);
        JobParams p1 = JobParams.builder().jobId("id1").jobName("name1").connectionProperties(conn).build();
        JobParams p2 = JobParams.builder().jobId("id1").jobName("name1").connectionProperties(conn).build();
        JobParams p3 = JobParams.builder().jobId("id2").jobName("name1").build();

        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
        assertNotEquals(p1, null);
        assertNotEquals(p1, new Object());
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1.hashCode(), p3.hashCode());
    }

    @Test
    void testClientGRPCJobParams() {
        ConnectionProperties conn = new ConnectionProperties("c1", "k1", "s1", "localhost", 8080, false);
        ClientGRPCJobParams params = new ClientGRPCJobParams("topic1", conn, "mn1");
        params.setJobName("job");

        assertEquals("topic1", params.getTopic());
        assertEquals("job-topic1", params.getJobId());
        assertEquals(conn, params.getConnectionProperties());
        assertEquals("mn1", params.getManagementNodeId());

        ClientGRPCJobParams p2 = new ClientGRPCJobParams("topic1", conn, "mn1");
        p2.setJobName("job");
        assertEquals(params, p2);
        assertEquals(params.hashCode(), p2.hashCode());
        assertEquals("job", params.toString());
    }

    @Test
    void testFileExchangeProperties() {
        FileExchangeProperties props = FileExchangeProperties.builder()
                .sourcePath("src")
                .destinationPath("dest")
                .build();

        assertEquals("src", props.getSourcePath());
        assertEquals("dest", props.getDestinationPath());

        FileExchangeProperties p2 = new FileExchangeProperties("src", "dest");
        assertEquals(props, p2);
        assertEquals(props.hashCode(), p2.hashCode());
        assertTrue(props.toString().contains("sourcePath='src'"));
        assertNotEquals(props, null);
    }

    @Test
    void testRecurrentJobRequest() {
        JobParams params = JobParams.builder().jobId("id").build();
        RecurrentJobRequest request = RecurrentJobRequest.builder()
                .jobParams(params)
                .build();

        assertEquals(params, request.getJobParams());
    }
}
