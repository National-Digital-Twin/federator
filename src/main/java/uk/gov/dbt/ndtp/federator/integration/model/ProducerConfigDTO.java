package uk.gov.dbt.ndtp.federator.integration.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProducerConfigDTO {
    private String clientId;
    private List<ProducerDTO> producers;
}
