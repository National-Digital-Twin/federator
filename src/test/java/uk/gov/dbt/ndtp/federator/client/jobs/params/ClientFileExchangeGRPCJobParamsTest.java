/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.client.jobs.params;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;

class ClientFileExchangeGRPCJobParamsTest {

    @Test
    void testConstructorAndAccessors() {
        FileExchangeProperties fep = new FileExchangeProperties("src", "dest");
        ConnectionProperties conn = new ConnectionProperties("id", "key", "server", "host", 8080, true);
        String topic = "test-topic";
        String nodeId = "node-1";

        ClientFileExchangeGRPCJobParams params = new ClientFileExchangeGRPCJobParams(fep, topic, conn, nodeId);

        assertEquals(fep, params.getFileExchangeProperties());
        assertEquals(topic, params.getTopic());
        assertEquals(conn, params.getConnectionProperties());
        assertEquals(nodeId, params.getManagementNodeId());
    }

    @Test
    void testEqualsAndHashCode() {
        FileExchangeProperties fep1 = new FileExchangeProperties("src", "dest");
        ConnectionProperties conn1 = new ConnectionProperties("id", "key", "server", "host", 8080, true);
        ClientFileExchangeGRPCJobParams params1 = new ClientFileExchangeGRPCJobParams(fep1, "topic", conn1, "node");

        FileExchangeProperties fep2 = new FileExchangeProperties("src", "dest");
        ConnectionProperties conn2 = new ConnectionProperties("id", "key", "server", "host", 8080, true);
        ClientFileExchangeGRPCJobParams params2 = new ClientFileExchangeGRPCJobParams(fep2, "topic", conn2, "node");

        assertEquals(params1, params2);
        assertEquals(params1.hashCode(), params2.hashCode());

        params2.setFileExchangeProperties(new FileExchangeProperties("other", "dest"));
        assertNotEquals(params1, params2);
    }

    @Test
    void testToString() {
        ClientFileExchangeGRPCJobParams params = new ClientFileExchangeGRPCJobParams();
        params.setJobName("TestJob");
        assertEquals("TestJob", params.toString());
    }
}
