// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
 */
package uk.gov.dbt.ndtp.federator.filter;

import static uk.gov.dbt.ndtp.federator.utils.HeaderUtils.getSecurityLabelFromHeaders;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.access.AccessMap;
import uk.gov.dbt.ndtp.federator.access.mappings.AccessAttributes;
import uk.gov.dbt.ndtp.federator.access.mappings.AccessDetails;
import uk.gov.dbt.ndtp.federator.exceptions.LabelException;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

/**
 * A message filter focusing on the Security Labels (User Attributes):
 * Nationality, Organisation, Clearance
 */
public class KafkaEventHeaderAttributeAccessFilterNationality implements MessageFilter<KafkaEvent<?, ?>> {

    public static final Logger LOGGER = LoggerFactory.getLogger("KafkaEventHeaderAttributeAccessFilterNationality");
    private AccessAttributes attributes;
    private final String clientId;

    // Ignore the linting messages saying this class/constructoris never used
    // it can be loaded using Class.forName within FilterReflectiveCreator
    public KafkaEventHeaderAttributeAccessFilterNationality(String clientId) {
        this.clientId = clientId;
        updateAttributes();
        LOGGER.info("Information clientId {}", clientId);
        LOGGER.info("Access attributes {}", attributes);
    }

    @Override
    public boolean filterOut(KafkaEvent<?, ?> message) throws LabelException {
        if (message == null) {
            LOGGER.error("Message is null. Cannot filter message correctly. Default to filtering out message");
            return true;
        }
        if (attributes == null) {
            LOGGER.error("Client attributes are null for client {}. Problems reading Access?", clientId);
            return true;
        }

        String secLabel = getSecurityLabelFromHeaders(message.headers());
        String msg = String.format(
                "Processing Message. SecLabel for message '%s', offset '%s', topic '%s'",
                secLabel,
                message.getConsumerRecord().offset(),
                message.getConsumerRecord().topic());
        LOGGER.info(msg);
        Map<String, String> map = getMapFromSecurityLabel(secLabel);
        return (filterOutMessageValueByAttribute(
                map, AccessAttributes.NATIONALITY_ATTRIBUTE, attributes.getNationality()));
    }

    private boolean filterOutMessageValueByAttribute(
            Map<String, String> map, String attributeName, String attributeValue) {
        String value = map.get(attributeName);
        if (null != value) {
            if (!value.equals(attributeValue.toUpperCase(Locale.ROOT))) {
                LOGGER.info("Filtering out '{}' as '{}' is not equal to '{}'", attributeName, value, attributeValue);
                return true;
            }
        }
        return false;
    }

    public static Map<String, String> getMapFromSecurityLabel(String securityLabel) throws LabelException {
        LOGGER.info("SecurityLabel - " + securityLabel);
        Map<String, String> securityLabelMap = new HashMap<>(3);
        if (!StringUtils.isBlank(securityLabel)) {
            for (String part : securityLabel.split(",")) {
                String[] parts = part.split("[=:]");
                if (2 == parts.length) {
                    String key = parts[0].toUpperCase(Locale.ROOT);
                    String value = parts[1].toUpperCase(Locale.ROOT);
                    LOGGER.info("Putting key - '{}' with value - '{}' into securityLabelMap", key, value);
                    securityLabelMap.put(key, value);
                } else {
                    // If we can't map the security label we should stop the service
                    String message = String.format("Cannot map security label: '%s'. Ignoring", part);
                    LOGGER.error(message);
                    throw new LabelException(message);
                }
            }
        }
        return securityLabelMap;
    }

    private void updateAttributes() {
        AccessDetails details = AccessMap.get().getDetails(clientId);
        if (null == details) {
            LOGGER.warn("No client Access details stored for {}", clientId);
            attributes = null;
            throw new RuntimeException(
                    String.format("Client Details '%s' cannot be found in access map. Need to stop!!", clientId));
        } else {
            attributes = details.getAttributes();
        }
    }

    @Override
    public void close() {
        // No resources to release in this class.
    }
}
