/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.storage.provider.file.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.gov.dbt.ndtp.federator.common.exception.FileTransferException;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.model.SourceType;
import uk.gov.dbt.ndtp.federator.exceptions.FileFetcherException;
import uk.gov.dbt.ndtp.federator.server.processor.file.FileTransferResult;

class LocalFileProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void testGetSuccess() throws IOException {
        Path tempFile = tempDir.resolve("test.txt");
        String content = "Hello, World!";
        Files.writeString(tempFile, content);

        LocalFileProvider provider = new LocalFileProvider();
        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, tempFile.toString());

        try (FileTransferResult result = provider.get(request)) {
            assertNotNull(result.stream());
            assertEquals(content.length(), result.fileSize());
            String resultDoc = new String(result.stream().readAllBytes());
            assertEquals(content, resultDoc);
        }
    }

    @Test
    void testGetFileNotFound() {
        LocalFileProvider provider = new LocalFileProvider();
        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, "non-existent-file");

        assertThrows(FileFetcherException.class, () -> provider.get(request));
    }

    @Test
    void testValidatePathSuccess() throws IOException {
        Path tempFile = tempDir.resolve("valid.txt");
        Files.writeString(tempFile, "content");

        LocalFileProvider provider = new LocalFileProvider();
        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, tempFile.toString());

        assertDoesNotThrow(() -> provider.validatePath(request));
    }

    @Test
    void testValidatePathFileNotFound() {
        LocalFileProvider provider = new LocalFileProvider();
        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, "/non/existent/file.txt");

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> provider.validatePath(request));
        assertTrue(exception.getMessage().contains("Local file does not exist"));
    }

    @Test
    void testValidatePathIsDirectory() throws IOException {
        Path dir = tempDir.resolve("subdir");
        Files.createDirectory(dir);

        LocalFileProvider provider = new LocalFileProvider();
        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, dir.toString());

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> provider.validatePath(request));
        assertTrue(exception.getMessage().contains("Local path is not a file"));
    }
}
