// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.common.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object for Data Provider entity.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DataProviderDTO {

    /**
     * Internal database identifier.
     */
    @JsonIgnore
    private Long id;

    /**
     * Provider name.
     */
    @JsonProperty("name")
    private String name;

    /**
     * Kafka topic.
     */
    @JsonProperty("topic")
    private String topic;

    /**
     * Provider description.
     */
    @JsonProperty("description")
    private String description;

    /**
     * Active status.
     */
    @JsonProperty("active")
    private Boolean active;

    /**
     * Producer identifier.
     */
    @JsonIgnore
    private Long producerId;

    /**
     * List of consumers.
     */
    @JsonProperty("consumers")
    @Builder.Default
    private List<ConsumerDTO> consumers = new ArrayList<>();
}
