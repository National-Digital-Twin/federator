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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.exceptions.LabelException;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

/**
 * A message filter focusing on the Security Labels (User Attributes):
 * Nationality, Organisation, Clearance
 */
public class KafkaEventHeaderAttributeAccessFilter implements MessageFilter<KafkaEvent<?, ?>> {

    public static final Logger LOGGER = LoggerFactory.getLogger("KafkaEventHeaderAttributeAccessFilter");
    private final String clientId;

    public KafkaEventHeaderAttributeAccessFilter(String clientId) {
        this.clientId = clientId;
    }

    public static Map<String, String> getMapFromSecurityLabel(String securityLabel) throws LabelException {
        LOGGER.debug("SecurityLabel - {} ", securityLabel);
        Map<String, String> securityLabelMap = new HashMap<>(3);
        if (!StringUtils.isBlank(securityLabel)) {
            for (String part : securityLabel.split(",")) {
                String[] parts = part.split("[=:]");
                if (2 == parts.length) {
                    String key = parts[0].toUpperCase(Locale.ROOT);
                    String value = parts[1].toUpperCase(Locale.ROOT);
                    LOGGER.debug("Putting key - '{}' with value - '{}' into securityLabelMap", key, value);
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

    @Override
    public boolean filterOut(KafkaEvent<?, ?> message) throws LabelException {
        throw new NotImplementedException("Use filterOut with AccessDetails");
    }

    @Override
    public void close() {
        // No resources to release in this class.
    }
}
