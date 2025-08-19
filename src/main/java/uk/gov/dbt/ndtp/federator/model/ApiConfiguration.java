package uk.gov.dbt.ndtp.federator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ApiConfiguration model
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiConfiguration {
    private boolean revoked;
    private String endpoint;
    private String apiKey;
    private int rateLimit;
}