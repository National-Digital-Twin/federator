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
import uk.gov.dbt.ndtp.federator.client.storage.ReceivedFileStorageFactory;
import uk.gov.dbt.ndtp.federator.client.storage.StoredFileResult;
import uk.gov.dbt.ndtp.federator.client.storage.impl.S3ReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenService;
import uk.gov.dbt.ndtp.federator.common.utils.GRPCUtils;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.RedisUtil;
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
        PropertyUtil.init("test.properties");

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
            clientUnderTest.processTopic(topic, seqId, "test-dest/");

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
        PropertyUtil.init("test.properties");

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
            FileAssemblyException ex = assertThrows(
                    FileAssemblyException.class, () -> clientUnderTest.processTopic(topic, seqId, "folder/"));
            assertTrue(ex.getMessage().contains("Unexpected error while processing file stream"));

            verify(redisMock, never()).setOffset(anyString(), anyString(), anyLong());
        } finally {
            PropertyUtil.clear();
        }
    }

    @Test
    void processTopic_withoutDestination_throws() {
        String client = "cli3";
        String key = "k3";
        String serverName = "srv3";
        String host = "localhost";
        int port = 50053;
        boolean tls = false;
        String topicPrefix = "tp3";
        String topic = "files.nodest";

        PropertyUtil.init("test.properties");
        // Mock GRPCUtils to avoid loading configuration/SSL during client construction
        IdpTokenService idpMock = mock(IdpTokenService.class);
        try (MockedStatic<GRPCUtils> grpcUtilsStatic =
                Mockito.mockStatic(GRPCUtils.class, Mockito.CALLS_REAL_METHODS)) {
            grpcUtilsStatic.when(GRPCUtils::createIdpTokenService).thenReturn(idpMock);

            GRPCFileClient clientUnderTest = new GRPCFileClient(client, key, serverName, host, port, tls, topicPrefix);
            assertThrows(FileAssemblyException.class, () -> clientUnderTest.processTopic(topic, 0L));
        } finally {
            PropertyUtil.clear();
        }
    }

    @Test
    void processTopic_s3UploadFailure_doesNotSaveOffset() {
        // Arrange
        String client = "cli4";
        String key = "k4";
        String serverName = "srv4";
        String host = "localhost";
        int port = 50054;
        boolean tls = false;
        String topicPrefix = "tp4";
        String topic = "files.s3fail";

        long seqId = 31L;
        byte[] data = "hello".getBytes();
        long fileSize = data.length;

        FileChunk dataChunk = FileChunk.newBuilder()
                .setFileName("s3.txt")
                .setChunkData(ByteString.copyFrom(data))
                .setChunkIndex(0)
                .setTotalChunks(1)
                .setIsLastChunk(false)
                .setFileSize(fileSize)
                .setFileSequenceId(seqId)
                .build();

        FileChunk lastChunk = FileChunk.newBuilder()
                .setFileName("s3.txt")
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
        PropertyUtil.init("test.properties");

        // Mock GRPCUtils.createIdpTokenService to avoid SSL/truststore creation
        IdpTokenService idpMock = mock(IdpTokenService.class);

        // S3 storage that simulates failed upload by returning null remoteUri
        class FailingS3 extends S3ReceivedFileStorage {
            @Override
            public StoredFileResult store(java.nio.file.Path localFile, String originalFileName, String destination) {
                return new StoredFileResult(localFile.toAbsolutePath(), null);
            }
        }

        try (MockedStatic<FederatorServiceGrpc> grpcStatic = Mockito.mockStatic(FederatorServiceGrpc.class);
                MockedStatic<RedisUtil> redisStatic = Mockito.mockStatic(RedisUtil.class);
                MockedStatic<GRPCUtils> grpcUtilsStatic =
                        Mockito.mockStatic(GRPCUtils.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<ReceivedFileStorageFactory> storageFactoryStatic =
                        Mockito.mockStatic(ReceivedFileStorageFactory.class)) {
            grpcStatic.when(() -> FederatorServiceGrpc.newBlockingStub(any())).thenReturn(stub);
            redisStatic.when(RedisUtil::getInstance).thenReturn(redisMock);
            grpcUtilsStatic.when(GRPCUtils::createIdpTokenService).thenReturn(idpMock);
            storageFactoryStatic.when(ReceivedFileStorageFactory::get).thenReturn(new FailingS3());

            GRPCFileClient clientUnderTest = new GRPCFileClient(client, key, serverName, host, port, tls, topicPrefix);

            // Act
            clientUnderTest.processTopic(topic, seqId, "s3-prefix/");

            // Assert: Redis offset must NOT be updated on S3 failure
            verify(redisMock, never()).setOffset(anyString(), anyString(), anyLong());
        } finally {
            PropertyUtil.clear();
        }
    }
}
