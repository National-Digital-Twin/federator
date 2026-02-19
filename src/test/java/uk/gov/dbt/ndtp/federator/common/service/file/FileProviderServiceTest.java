/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.service.file;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.model.SourceType;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.FileProvider;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.FileProviderFactory;
import uk.gov.dbt.ndtp.federator.server.processor.file.FileTransferResult;

class FileProviderServiceTest {

    @Test
    void testGetFile() {
        FileTransferRequest request = mock(FileTransferRequest.class);
        when(request.sourceType()).thenReturn(SourceType.S3);

        FileProvider provider = mock(FileProvider.class);
        FileTransferResult result = mock(FileTransferResult.class);
        when(provider.get(request)).thenReturn(result);

        try (MockedStatic<FileProviderFactory> factoryMock = mockStatic(FileProviderFactory.class)) {
            factoryMock
                    .when(() -> FileProviderFactory.getProvider(SourceType.S3))
                    .thenReturn(provider);

            FileProviderService service = new FileProviderService();
            FileTransferResult actual = service.getFile(request);

            assertEquals(result, actual);
            verify(provider).get(request);
        }
    }
}
