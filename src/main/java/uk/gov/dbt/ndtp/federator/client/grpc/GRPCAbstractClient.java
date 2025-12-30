// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
package uk.gov.dbt.ndtp.federator.client.grpc;

import io.grpc.ManagedChannel;
import java.util.concurrent.TimeUnit;
import uk.gov.dbt.ndtp.grpc.FederatorServiceGrpc;

/**
 * Abstract base for GRPC clients providing common fields and lifecycle.
 *
 * <p>Holds the underlying gRPC {@link ManagedChannel}, the generated
 * {@link uk.gov.dbt.ndtp.grpc.FederatorServiceGrpc.FederatorServiceBlockingStub}, and common
 * identity/namespace fields. Subclasses should focus on domain-specific behaviour.
 */
public abstract class GRPCAbstractClient implements GRPCClient {

    protected final ManagedChannel channel;
    protected final FederatorServiceGrpc.FederatorServiceBlockingStub blockingStub;
    protected final String client;
    protected final String key;
    protected final String topicPrefix;
    protected final String serverName;

    /**
     * Construct with explicit parameters. Builds the channel once via {@link #buildChannel}.
     */
    protected GRPCAbstractClient(
            String client,
            String key,
            String serverName,
            String host,
            int port,
            boolean isTLSEnabled,
            String topicPrefix) {
        this.topicPrefix = topicPrefix;
        this.client = client;
        this.key = key;
        this.serverName = serverName;
        ManagedChannel ch = buildChannel(host, port, isTLSEnabled);
        this.channel = ch;
        this.blockingStub = (ch != null) ? FederatorServiceGrpc.newBlockingStub(this.channel) : null;
    }

    /**
     * Construct with explicit identity and an already-created channel (preferred for testability).
     */
    protected GRPCAbstractClient(
            String client, String key, String serverName, String topicPrefix, ManagedChannel channel) {
        this.topicPrefix = topicPrefix;
        this.client = client;
        this.key = key;
        this.serverName = serverName;
        if (channel == null) {
            throw new IllegalArgumentException("ManagedChannel must not be null");
        }
        this.channel = channel;
        this.blockingStub = FederatorServiceGrpc.newBlockingStub(this.channel);
    }

    /**
     * Common Redis prefix based on client and server name.
     */
    public String getRedisPrefix() {
        return getRedisPrefix(this.client, this.serverName);
    }

    /**
     * Hook for building the channel. By default delegates to generateChannel(...).
     * Tests may override generateChannel(...) or this method to inject a mock.
     */
    protected ManagedChannel buildChannel(String host, int port, boolean isTLSEnabled) {
        return generateChannel(host, port, isTLSEnabled);
    }

    /**
     * Returns the blocking stub for this client.
     */
    protected final FederatorServiceGrpc.FederatorServiceBlockingStub getStub() {
        return this.blockingStub;
    }

    /**
     * Exposes the underlying channel for tests.
     */
    protected final ManagedChannel getChannel() {
        return this.channel;
    }

    /**
     * Close the underlying channel with a short timeout.
     */
    @Override
    public void close() {
        try {
            if (channel != null) {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Client close interrupted", ie);
        } catch (Exception t) {
            LOGGER.error("Unexpected error while closing client", t);
        }
    }
}
