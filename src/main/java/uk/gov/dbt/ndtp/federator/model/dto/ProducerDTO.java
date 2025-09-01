// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object for organization producer entity.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProducerDTO {

    /**
     * Internal database identifier.
     */
    @JsonIgnore
    private Long id;

    /**
     * Producer name.
     */
    private String name;

    /**
     * Producer description.
     */
    private String description;

    /**
     * Organization identifier.
     */
    @JsonIgnore
    private Long orgId;

    /**
     * Producer active status.
     */
    private Boolean active;

    /**
     * Host address.
     */
    private String host;

    /**
     * Port number.
     */
    private Integer port;

    /**
     * TLS enabled flag.
     */
    private Boolean tls;

    /**
     * Identity Provider client identifier.
     */
    private String idpClientId;

    /**
     * List of data providers.
     */
    private final List<ProductDTO> dataProviders = new ArrayList<>();
}
