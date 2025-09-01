// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.model.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for producer configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProducerConfigDTO {

    /**
     * Unique identifier for the producer client.
     */
    private String clientId;

    /**
     * List of producers associated with this configuration.
     */
    private List<ProducerDTO> producers;
}
