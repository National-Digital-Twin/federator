package uk.gov.dbt.ndtp.federator.server.processor.file;

import uk.gov.dbt.ndtp.federator.common.exception.FileTransferException;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.FileProvider;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.FileProviderFactory;

/**
 * Validator for FileTransferRequest that checks all required conditions before file transfer.
 * Extracted for better modularity and testability.
 * Path validation is delegated to provider-specific implementations.
 */
public class FileTransferRequestValidator {

    /**
     * Validates a FileTransferRequest.
     *
     * @param request the FileTransferRequest to validate
     * @throws IllegalArgumentException if the request is null or path is blank
     * @throws FileTransferException if sourceType is null or path is not correct
     */
    public void validate(FileTransferRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("FileTransferRequest is null");
        }
        if (request.path() == null || request.path().isBlank()) {
            throw new IllegalArgumentException("FileTransferRequest.path is blank");
        }
        if (request.sourceType() == null) {
            throw new FileTransferException("FileTransferRequest.sourceType is null");
        }

        // Delegate path validation to the appropriate provider
        FileProvider provider = FileProviderFactory.getProvider(request.sourceType());
        provider.validatePath(request);
    }
}
