package uk.gov.dbt.ndtp.federator.model;

import java.util.Objects;

public record FileFetchRequest(
        SourceType sourceType,
        String bucketOrContainer, // null or blank for LOCAL
        String path // path
        ) {
    public FileFetchRequest {
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        Objects.requireNonNull(path, "path must not be null");
    }
}
