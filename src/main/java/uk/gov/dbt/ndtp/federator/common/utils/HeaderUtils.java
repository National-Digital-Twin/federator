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

package uk.gov.dbt.ndtp.federator.common.utils;

import static uk.gov.dbt.ndtp.secure.agent.sources.IANodeHeaders.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.exceptions.LabelException;
import uk.gov.dbt.ndtp.grpc.Headers;
import uk.gov.dbt.ndtp.secure.agent.sources.Header;

/**
 * Utility class for working with headers
 */
public class HeaderUtils {

    public static final Logger LOGGER = LoggerFactory.getLogger("RdfKafkaEventMessagePostProcessor");

    private HeaderUtils() {}

    public static String getSecurityLabelFromHeaders(Stream<Header> headerStream) {
        return headerStream
                .filter(h -> h.key().equalsIgnoreCase(SECURITY_LABEL))
                .map(Header::value)
                .findFirst()
                .orElse("");
    }

    public static List<Headers> selectHeaders(Stream<Header> headerStream, Set<String> headerKeys) {
        return headerStream
                .filter(h -> headerKeys.contains(h.key()))
                .map(h ->
                        Headers.newBuilder().setKey(h.key()).setValue(h.value()).build())
                .collect(Collectors.toList());
    }

    public static List<Headers> selectHeaders(Stream<Header> headerStream) {
        return headerStream
                .map(h ->
                        Headers.newBuilder().setKey(h.key()).setValue(h.value()).build())
                .collect(Collectors.toList());
    }

    public static Map<String, String> getMapFromSecurityLabel(String securityLabel) throws LabelException {
        LOGGER.debug("SecurityLabel - {} ", securityLabel);
        return SecurityLabelUtil.parse(securityLabel).asMap();
    }
}
