// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.common.management;

import uk.gov.dbt.ndtp.federator.common.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerConfigDTO;

/**
 * Interface for handling communication with the Management Node.
 * Provides methods to retrieve producer and consumer configurations.
 */
public interface ManagementNodeDataHandlerInterface {

    /**
     * Retrieves producer configuration data from the Management Node.
     *
     * @param producerId producer ID for retrieving specific configuration,
     *                   may be null
     * @return ProducerConfigDTO containing producer configuration
     * @throws ManagementNodeDataException if communication with
     *         Management Node fails
     */
    ProducerConfigDTO getProducerData(String producerId) throws ManagementNodeDataException;

    /**
     * Retrieves consumer configuration data from the Management Node.
     *
     * @param consumerId consumer ID for retrieving specific configuration,
     *                   may be null
     * @return ConsumerConfigDTO containing consumer configuration
     * @throws ManagementNodeDataException if communication with
     *         Management Node fails
     */
    ConsumerConfigDTO getConsumerData(String consumerId) throws ManagementNodeDataException;
}
