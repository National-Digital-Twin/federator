package uk.gov.dbt.ndtp.federator.common.storage.provider.file;

import uk.gov.dbt.ndtp.federator.common.exception.FileTransferException;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.server.processor.file.FileTransferResult;

/**
 * Contract for components capable of fetching a file stream and its size from a remote source.
 */
public interface FileProvider {

    /**
     * Fetches a file described by the given request, returning an input stream and the known size.
     * Implementations should throw a domain-specific runtime exception when the file is not found
     * or cannot be accessed.
     *
     * @param request description of the remote file to retrieve
     * @return {@link FileTransferResult} containing an {@code InputStream} and the content length
     */
    FileTransferResult get(FileTransferRequest request);

    /**
     * Validates that the path in the request is correct for this provider.
     * Each provider implementation should define what constitutes a valid path for its specific storage type.
     *
     * @param request the file transfer request containing the path to validate
     * @throws FileTransferException if the path is not valid for this provider
     */
    void validatePath(FileTransferRequest request);

    /**
     * Validates that the storage container is not null or blank.
     * This is a common validation for cloud storage providers (S3, Azure) that require a container/bucket.
     *
     * @param request the file transfer request containing the storage container to validate
     * @param containerName the name of the container type (for error messages, e.g., "S3 bucket", "Azure container")
     * @throws FileTransferException if the storage container is null or blank
     */
    default void validateStorageContainer(FileTransferRequest request, String containerName) {
        if (request.storageContainer() == null || request.storageContainer().isBlank()) {
            throw new FileTransferException(containerName + " (storageContainer) is required");
        }
    }

    /**
     * Wraps validation logic with common exception handling.
     * This ensures FileTransferException is rethrown as-is, while other exceptions are wrapped.
     *
     * @param validationLogic the validation logic to execute
     * @param errorMessage the error message to use when wrapping non-FileTransferException exceptions
     * @throws FileTransferException if validation fails
     */
    default void executeValidation(ValidationLogic validationLogic, String errorMessage) {
        try {
            validationLogic.validate();
        } catch (FileTransferException e) {
            throw e;
        } catch (Exception e) {
            throw new FileTransferException(errorMessage, e);
        }
    }

    /**
     * Functional interface for validation logic that can throw any exception.
     */
    @FunctionalInterface
    interface ValidationLogic {
        void validate() throws Exception;
    }
}
