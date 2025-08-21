package uk.gov.dbt.ndtp.federator.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.util.List;

/**
 * DTO for OrganisationDataProvider entity.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {
    @JsonIgnore
    private Long id;
    private String name;
    private String topic;
    @JsonIgnore
    private Long producerId;
    private List<ConsumerDTO> consumers;
}