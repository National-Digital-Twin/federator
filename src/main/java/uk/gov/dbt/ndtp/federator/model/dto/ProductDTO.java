// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object for organization data provider entity.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {

    /**
     * Internal database identifier.
     */
    @JsonIgnore
    private Long id;

    /**
     * Product name.
     */
    private String name;

    /**
     * Kafka topic.
     */
    private String topic;

    /**
     * Producer identifier.
     */
    @JsonIgnore
    private Long producerId;

    /**
     * List of consumers.
     */
    private List<ConsumerDTO> consumers;
}
