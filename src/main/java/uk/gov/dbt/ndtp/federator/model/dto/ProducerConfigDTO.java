package uk.gov.dbt.ndtp.federator.model.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Producer Configuration DTO
 */
@Builder
@Getter
@Setter
public class ProducerConfigDTO {
    private String clientId;
    private List<ProducerDTO> producers;
}