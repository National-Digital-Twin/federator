package uk.gov.dbt.ndtp.federator.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for Data Provider entity
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DataProviderDTO {

    @JsonIgnore
    private Long id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("topic")
    private String topic;

    @JsonProperty("description")
    private String description;

    @JsonProperty("active")
    private Boolean active;

    @JsonIgnore
    private Long producerId;

    @JsonProperty("consumers")
    @Builder.Default
    private List<ConsumerDTO> consumers = new ArrayList<>();
}