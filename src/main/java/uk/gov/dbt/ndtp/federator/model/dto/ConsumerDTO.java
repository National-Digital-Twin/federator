package uk.gov.dbt.ndtp.federator.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

/**
 * DTO for consumerId entity.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerDTO {
    @JsonIgnore
    private Long id;
    private String name;
    @JsonIgnore
    private Long orgId;
    private String idpClientId;
}