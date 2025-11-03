package uk.gov.dbt.ndtp.federator.common.model;

import java.util.Objects;

public record FileTransferRequest(
        SourceType sourceType,
        String storageContainer, // null or blank for LOCAL
        String path // path
        ) {
    public FileTransferRequest {
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        Objects.requireNonNull(path, "path must not be null");
    }
}
