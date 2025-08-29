// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for consumer allowed data provider entity.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductConsumerDTO {

    /**
     * Product identifier.
     */
    private Long productId;

    /**
     * Consumer identifier.
     */
    private Long consumerId;

    /**
     * Access grant timestamp.
     */
    private LocalDateTime grantedTs;

    /**
     * Validity period in days.
     */
    private Integer validity;
}