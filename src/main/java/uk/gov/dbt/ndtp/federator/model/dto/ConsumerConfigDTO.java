package uk.gov.dbt.ndtp.federator.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumerConfigDTO {
  private String clientId;
  private List<ProducerDTO> producers;
}