// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.
package uk.gov.dbt.ndtp.federator.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for OrganisationProducer entity.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProducerDTO {
    @JsonIgnore
    private Long id;
    private String name;
    private String description;
    @JsonIgnore
    private Long orgId;
    private Boolean active;
    private String host;
    private BigDecimal port;
    private Boolean tls;
    private String idpClientId;
    private final List<ProductDTO> dataProviders = new ArrayList<>();
}