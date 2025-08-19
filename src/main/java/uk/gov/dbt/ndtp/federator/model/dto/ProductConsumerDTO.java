package uk.gov.dbt.ndtp.federator.model.dto;

import lombok.*;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * DTO for ConsumerAllowedDataProvider entity.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductConsumerDTO {
    private Long productId;
    private Long consumerId;
    private Timestamp grantedTs;
    private BigDecimal validity;
}