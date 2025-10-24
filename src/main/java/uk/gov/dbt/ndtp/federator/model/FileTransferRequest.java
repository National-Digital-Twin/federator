package uk.gov.dbt.ndtp.federator.model;

import java.util.Objects;

public record FileTransferRequest(
        SourceType sourceType,
        String bucketOrContainer, // null or blank for LOCAL
        String path // path
        ) {
    public FileTransferRequest {
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        Objects.requireNonNull(path, "path must not be null");
    }
}
