/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for OrganisationProducer entity.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProducerDTO {
    private List<ProductDTO> products = new ArrayList<>();

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
}
