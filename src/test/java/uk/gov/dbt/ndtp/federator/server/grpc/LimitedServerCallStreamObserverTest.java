/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.server.grpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.stub.ServerCallStreamObserver;
import org.junit.jupiter.api.Test;

class LimitedServerCallStreamObserverTest {

    @Test
    void delegatedMethods_work() {
        ServerCallStreamObserver<String> mockObserver = mock(ServerCallStreamObserver.class);
        LimitedServerCallStreamObserver<String> observer = new LimitedServerCallStreamObserver<>(mockObserver);

        Runnable handler = () -> {};
        observer.setOnCancelHandler(handler);
        verify(mockObserver).setOnCancelHandler(handler);

        when(mockObserver.isCancelled()).thenReturn(true);
        assertTrue(observer.isCancelled());

        observer.onNext("value");
        verify(mockObserver).onNext("value");

        Exception ex = new Exception("error");
        observer.onError(ex);
        verify(mockObserver).onError(ex);

        observer.onCompleted();
        verify(mockObserver).onCompleted();
    }
}
