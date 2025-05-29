// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object for consumer configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumerConfigDTO {

  /**
   * Unique identifier for the consumer client.
   */
  private String clientId;

  /**
   * List of producers associated with this consumer.
   */
  private List<ProducerDTO> producers;
}