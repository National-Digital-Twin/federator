package uk.gov.dbt.ndtp.federator.client.grpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenService;
import uk.gov.dbt.ndtp.federator.common.utils.GRPCUtils;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.RedisUtil;
import uk.gov.dbt.ndtp.federator.common.utils.TestPropertyUtil;
import uk.gov.dbt.ndtp.federator.exceptions.FileAssemblyException;
import uk.gov.dbt.ndtp.grpc.FederatorServiceGrpc;
import uk.gov.dbt.ndtp.grpc.FileChunk;
import uk.gov.dbt.ndtp.grpc.FileStreamRequest;

class GRPCFileClientTest {

    @Test
    void processTopic_success_savesNextSequenceToRedis() {
        // Arrange
        String client = "cli";
        String key = "k";
        String serverName = "srv";
        String host = "localhost";
        int port = 50051;
        boolean tls = false;
        String topicPrefix = "tp";
        String topic = "files.topic";

        long seqId = 10L;
        byte[] data = "abc".getBytes();
        long fileSize = data.length;

        FileChunk dataChunk = FileChunk.newBuilder()
                .setFileName("f.txt")
                .setChunkData(ByteString.copyFrom(data))
                .setChunkIndex(0)
                .setTotalChunks(1)
                .setIsLastChunk(false)
                .setFileSize(fileSize)
                .setFileSequenceId(seqId)
                .build();

        FileChunk lastChunk = FileChunk.newBuilder()
                .setFileName("f.txt")
                .setChunkIndex(1)
                .setTotalChunks(1)
                .setIsLastChunk(true)
                .setFileSize(fileSize)
                .setFileSequenceId(seqId)
                .build();

        Iterator<FileChunk> iterator = List.of(dataChunk, lastChunk).iterator();

        FederatorServiceGrpc.FederatorServiceBlockingStub stub =
                mock(FederatorServiceGrpc.FederatorServiceBlockingStub.class);
        when(stub.getFilesStream(any(FileStreamRequest.class))).thenReturn(iterator);

        RedisUtil redisMock = mock(RedisUtil.class);

        // Initialize properties from test resources to avoid mocking PropertyUtil internals
        TestPropertyUtil.setUpProperties();

        // Mock GRPCUtils.createIdpTokenService to avoid SSL/truststore creation
        IdpTokenService idpMock = mock(IdpTokenService.class);

        try (MockedStatic<FederatorServiceGrpc> grpcStatic = Mockito.mockStatic(FederatorServiceGrpc.class);
                MockedStatic<RedisUtil> redisStatic = Mockito.mockStatic(RedisUtil.class);
                MockedStatic<GRPCUtils> grpcUtilsStatic =
                        Mockito.mockStatic(GRPCUtils.class, Mockito.CALLS_REAL_METHODS)) {
            grpcStatic.when(() -> FederatorServiceGrpc.newBlockingStub(any())).thenReturn(stub);
            redisStatic.when(RedisUtil::getInstance).thenReturn(redisMock);
            grpcUtilsStatic.when(GRPCUtils::createIdpTokenService).thenReturn(idpMock);

            GRPCFileClient clientUnderTest = new GRPCFileClient(client, key, serverName, host, port, tls, topicPrefix);

            // Act
            clientUnderTest.processTopic(topic, seqId);

            // Assert
            String expectedPrefix = client + "-" + serverName;
            verify(redisMock, times(1)).setOffset(expectedPrefix, topic, seqId + 1);
        } finally {
            PropertyUtil.clear();
        }
    }

    @Test
    void processTopic_checksumMismatch_throwsFileAssemblyException_andDoesNotSaveOffset() {
        // Arrange
        String client = "cli2";
        String key = "k2";
        String serverName = "srv2";
        String host = "localhost";
        int port = 50052;
        boolean tls = false;
        String topicPrefix = "tp2";
        String topic = "files.bad";

        long seqId = 21L;
        byte[] data = "xyz".getBytes();
        long fileSize = data.length;

        FileChunk dataChunk = FileChunk.newBuilder()
                .setFileName("bad.txt")
                .setChunkData(ByteString.copyFrom(data))
                .setChunkIndex(0)
                .setTotalChunks(1)
                .setIsLastChunk(false)
                .setFileSize(fileSize)
                .setFileSequenceId(seqId)
                .build();

        FileChunk lastChunk = FileChunk.newBuilder()
                .setFileName("bad.txt")
                .setChunkIndex(1)
                .setTotalChunks(1)
                .setIsLastChunk(true)
                .setFileChecksum("deadbeef")
                .setFileSize(fileSize)
                .setFileSequenceId(seqId)
                .build();

        Iterator<FileChunk> iterator = List.of(dataChunk, lastChunk).iterator();

        FederatorServiceGrpc.FederatorServiceBlockingStub stub =
                mock(FederatorServiceGrpc.FederatorServiceBlockingStub.class);
        when(stub.getFilesStream(any(FileStreamRequest.class))).thenReturn(iterator);

        RedisUtil redisMock = mock(RedisUtil.class);

        // Initialize properties from test resources to avoid mocking PropertyUtil internals
        TestPropertyUtil.setUpProperties();

        // Mock GRPCUtils.createIdpTokenService to avoid SSL/truststore creation
        IdpTokenService idpMock = mock(IdpTokenService.class);

        try (MockedStatic<FederatorServiceGrpc> grpcStatic = Mockito.mockStatic(FederatorServiceGrpc.class);
                MockedStatic<RedisUtil> redisStatic = Mockito.mockStatic(RedisUtil.class);
                MockedStatic<GRPCUtils> grpcUtilsStatic =
                        Mockito.mockStatic(GRPCUtils.class, Mockito.CALLS_REAL_METHODS)) {
            grpcStatic.when(() -> FederatorServiceGrpc.newBlockingStub(any())).thenReturn(stub);
            redisStatic.when(RedisUtil::getInstance).thenReturn(redisMock);
            grpcUtilsStatic.when(GRPCUtils::createIdpTokenService).thenReturn(idpMock);

            GRPCFileClient clientUnderTest = new GRPCFileClient(client, key, serverName, host, port, tls, topicPrefix);

            // Act + Assert
            FileAssemblyException ex =
                    assertThrows(FileAssemblyException.class, () -> clientUnderTest.processTopic(topic, seqId));
            assertTrue(ex.getMessage().contains("Unexpected error while processing file stream"));

            verify(redisMock, never()).setOffset(anyString(), anyString(), anyLong());
        } finally {
            PropertyUtil.clear();
        }
    }
}
