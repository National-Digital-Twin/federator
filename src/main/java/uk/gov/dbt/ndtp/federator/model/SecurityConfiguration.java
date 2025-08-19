package uk.gov.dbt.ndtp.federator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SecurityConfiguration model
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityConfiguration {
    private String protocol;
    private String saslMechanism;
    private String saslJaasConfig;
    private String truststoreLocation;
    private String truststorePassword;
    private String keystoreLocation;
    private String keystorePassword;
}