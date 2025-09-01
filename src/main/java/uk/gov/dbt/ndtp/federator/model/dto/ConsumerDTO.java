// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object representing a consumer entity.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerDTO {

    /**
     * Internal database identifier.
     */
    @JsonIgnore
    private Long id;

    /**
     * Consumer name.
     */
    private String name;

    /**
     * Organization identifier.
     */
    @JsonIgnore
    private Long orgId;

    /**
     * Identity Provider client identifier.
     */
    private String idpClientId;
}
