/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.server.processor.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.gov.dbt.ndtp.federator.common.exception.FileTransferException;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.model.SourceType;

class FileTransferRequestValidatorTest {

    @TempDir
    File tempDir;

    private FileTransferRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileTransferRequestValidator();
    }

    @Test
    void testValidateWithValidRequest() throws IOException {
        // Given
        File validFile = new File(tempDir, "valid-file.txt");
        Files.writeString(validFile.toPath(), "test content");
        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, validFile.getAbsolutePath());

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void testValidateWithNullRequest() {
        // Given
        FileTransferRequest request = null;

        // When/Then
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> validator.validate(request));
        assertEquals("FileTransferRequest is null", exception.getMessage());
    }

    @Test
    void testValidateWithBlankPath() {
        // Given
        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, "   ");

        // When/Then
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> validator.validate(request));
        assertEquals("FileTransferRequest.path is blank", exception.getMessage());
    }

    @Test
    void testValidateWithNullPath() {
        // Given - FileTransferRequest constructor will throw on null path
        // When/Then
        assertThrows(NullPointerException.class, () -> {
            new FileTransferRequest(SourceType.LOCAL, null, null);
        });
    }

    @Test
    void testValidateWithNullSourceType() {
        // Given - FileTransferRequest constructor will throw on null sourceType
        // When/Then
        assertThrows(NullPointerException.class, () -> {
            new FileTransferRequest(null, null, "/some/path");
        });
    }

    @Test
    void testValidateWithNonExistentPath() {
        // Given
        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, "/non/existent/path/file.txt");

        // When/Then
        FileTransferException exception = assertThrows(FileTransferException.class, () -> validator.validate(request));
        assertTrue(exception.getMessage().contains("Local file does not exist")
                || exception.getMessage().contains("Invalid local file path"));
    }

    @Test
    void testValidateWithDirectoryPath() throws IOException {
        // Given - path points to a directory, not a file
        File directory = new File(tempDir, "subdir");
        directory.mkdir();
        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, directory.getAbsolutePath());

        // When/Then
        FileTransferException exception = assertThrows(FileTransferException.class, () -> validator.validate(request));
        assertTrue(exception.getMessage().contains("Local path is not a file")
                || exception.getMessage().contains("Invalid local file path"));
    }
}
