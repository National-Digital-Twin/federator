// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.
package uk.gov.dbt.ndtp.federator.management;

import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;

import java.util.Optional;

/**
 * Interface for handling communication with the Management Node.
 * Provides methods to retrieve producer and consumer configurations
 * with optional ID parameters for specific configurations.
 */
public interface ManagementNodeDataHandlerInterface {

    /**
     * Retrieves producer configuration data from the Management Node.
     *
     * @param producerId optional producer ID for retrieving specific configuration
     * @return ProducerConfigDTO containing producer configuration
     * @throws ManagementNodeDataException if communication with Management Node fails
     */
    ProducerConfigDTO getProducerData(Optional<String> producerId) throws ManagementNodeDataException;

    /**
     * Retrieves consumer configuration data from the Management Node.
     *
     * @param consumerId optional consumer ID for retrieving specific configuration
     * @return ConsumerConfigDTO containing consumer configuration
     * @throws ManagementNodeDataException if communication with Management Node fails
     */
    ConsumerConfigDTO getConsumerData(Optional<String> consumerId) throws ManagementNodeDataException;

    /**
     * Tests connectivity to the Management Node.
     *
     * @return true if Management Node is reachable, false otherwise
     */
    boolean checkConnectivity();

    /**
     * Gets the base URL of the Management Node.
     *
     * @return the Management Node base URL
     */
    String getManagementNodeBaseUrl();

    /**
     * Checks if the handler is properly configured.
     *
     * @return true if all required dependencies are configured
     */
    boolean isConfigured();

    /**
     * Gets the configured request timeout.
     *
     * @return request timeout in seconds
     */
    long getRequestTimeoutSeconds();
}