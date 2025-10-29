// SPDX-License-Identifier: Apache-2.0
package uk.gov.dbt.ndtp.federator.common.service.file;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.Context;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.server.conductor.FileConductor;
import uk.gov.dbt.ndtp.federator.server.grpc.GRPCContextKeys;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.FileChunk;
import uk.gov.dbt.ndtp.grpc.FileStreamRequest;

class FileStreamServiceTest {

    @Test
    void test_streamToClient_invokesConductorAndCompletes() throws Exception {
        FileStreamService cut = new FileStreamService();

        StreamObservable<FileChunk> observer = mock(StreamObservable.class);

        FileStreamRequest req = FileStreamRequest.newBuilder()
                .setTopic("files-topic")
                .setStartSequenceId(0L)
                .build();

        try (MockedConstruction<FileConductor> mocked = Mockito.mockConstruction(
                FileConductor.class, (mock, ctx) -> doNothing().when(mock).close())) {

            // Put client id into gRPC Context so service can read it
            Context grpcCtx = Context.current().withValue(GRPCContextKeys.CLIENT_ID, "client-xyz");
            Context prev = grpcCtx.attach();
            try {
                cut.streamToClient(req, observer);
            } finally {
                grpcCtx.detach(prev);
            }

            // One FileConductor constructed
            assertEquals(1, mocked.constructed().size());

            // Cancel handler is set and onCompleted called
            verify(observer, times(1)).setOnCancelHandler(any());
            verify(observer, times(1)).onCompleted();
        }
    }
}
