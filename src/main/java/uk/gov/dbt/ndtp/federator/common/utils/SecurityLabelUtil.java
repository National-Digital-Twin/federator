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

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import uk.gov.dbt.ndtp.federator.exceptions.LabelException;

/**
 * Value object representing a parsed SECURITY_LABEL header.
 *
 * Parsing overview
 * - Input format: A comma-separated list of key-value pairs. For each pair, the key and value are
 *   separated by either '=' or ':'. Examples:
 *   - "A=B"
 *   - "A=B,C=D"
 *   - " nationality : uk , clearance=secret "
 * - Whitespace around keys, values, and delimiters is ignored (trimmed).
 * - Case handling: keys and values are normalized to uppercase and stored in an immutable map.
 *   Lookups should therefore use uppercase keys, or rely on higher-level callers to normalize.
 * - Empty segments between commas are ignored. Example: ",A=B,,C=D," produces {A=B, C=D}.
 * - Error handling: If a non-empty segment cannot be split into a non-empty key and value using
 *   either '=' or ':', a {@link uk.gov.dbt.ndtp.federator.exceptions.LabelException} is thrown.
 *   Examples that cause an exception: "A" (no delimiter), "A= " (empty value), " =B" (empty key).
 * - Thread-safety: Instances are immutable and therefore thread-safe.
 *
 * This class encapsulates parsing and stores an uppercase canonical representation for keys and
 * values to ease case-insensitive matching.
 */
@Slf4j
@Value
public class SecurityLabelUtil {

    Map<String, String> attributes; // canonical uppercase key -> uppercase value

    private SecurityLabelUtil(Map<String, String> attributes) {
        this.attributes = Collections.unmodifiableMap(attributes);
    }

    public static SecurityLabelUtil parse(String raw) throws LabelException {
        Map<String, String> map = new HashMap<>();
        if (StringUtils.isBlank(raw)) {
            return new SecurityLabelUtil(map);
        }
        for (String rawPart : raw.split(",")) {
            String part = rawPart == null ? "" : rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            putPair(map, part);
        }
        return new SecurityLabelUtil(map);
    }

    private static void putPair(Map<String, String> map, String part) throws LabelException {
        int idx = delimiterIndex(part);
        if (idx <= 0 || idx >= part.length() - 1) {
            String message = String.format("Cannot map security label: '%s'. Ignoring", part);
            log.error(message);
            throw new LabelException(message);
        }
        String key = part.substring(0, idx).trim().toUpperCase(Locale.ROOT);
        String value = part.substring(idx + 1).trim().toUpperCase(Locale.ROOT);
        if (key.isEmpty() || value.isEmpty()) {
            String message = String.format("Cannot map security label: '%s'. Ignoring", part);
            log.error(message);
            throw new LabelException(message);
        }
        log.debug("Putting key - '{}' with value - '{}' into securityLabelMap", key, value);
        map.put(key, value);
    }

    private static int delimiterIndex(String part) {
        int eq = part.indexOf('=');
        int col = part.indexOf(':');
        if (eq == -1) return col;
        if (col == -1) return eq;
        return Math.min(eq, col);
    }

    public Map<String, String> asMap() {
        return attributes;
    }
}
