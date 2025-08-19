package uk.gov.dbt.ndtp.federator.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for Producer entity
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProducerDTO {

    @JsonIgnore
    private Long id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonIgnore
    private Long orgId;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("host")
    private String host;

    @JsonProperty("port")
    private BigDecimal port;

    @JsonProperty("tls")
    private Boolean tls;

    @JsonProperty("idpClientId")
    private String idpClientId;

    @JsonProperty("dataProviders")
    @Builder.Default
    private List<DataProviderDTO> dataProviders = new ArrayList<>();
}