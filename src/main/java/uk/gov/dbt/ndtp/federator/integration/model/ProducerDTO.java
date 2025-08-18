package uk.gov.dbt.ndtp.federator.integration.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** DTO for OrganisationProducer entity. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProducerDTO {

  private String name;
  private String description;
  private Boolean active;
  private String host;
  private BigDecimal port;
  private Boolean tls;
  private String idpClientId;
  @Default private List<ProductDTO> dataProviders = new ArrayList<>();
}
