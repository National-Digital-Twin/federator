// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

/*
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

package uk.gov.dbt.ndtp.federator.common.utils;

import java.util.function.Supplier;
import java.util.regex.Pattern;

public class StringUtils {

    private StringUtils() {
        // Prevent instantiation
    }

    /**
     * Checks if a String is empty (""), null or whitespace only.
     * <p>
     * Blankness of a String is defined by {@link String#isBlank()}.
     *
     * <pre>
     * StringUtils.throwIfBlank(null, () -> new Exception())      = thrown exception
     * StringUtils.throwIfBlank("", () -> new Exception())        = thrown exception
     * StringUtils.throwIfBlank(" ", () -> new Exception())       = thrown exception
     * StringUtils.throwIfBlank("bob", () -> new Exception())     = no exception
     * StringUtils.throwIfBlank("  bob  ", () -> new Exception()) = no exception
     * </pre>
     * @param value Value to check, may be null
     * @param exception Supplier of the exception
     * @param <E> Type of exception to throw
     * @throws E The exception when {@code value} is blank or null
     */
    public static <E extends Exception> void throwIfBlank(String value, Supplier<E> exception) throws E {
        if (value == null || value.isBlank()) {
            throw exception.get();
        }
    }

    /**
     * Checks if a String matches a RegEx Pattern.
     * <p>
     *
     * Given pattern "^[a-zA-Z0-9][a-zA-Z0-9-]*"
     *
     * <p>
     * <pre>
     * StringUtils.throwIfNotMatch(null, () -> new Exception(), pattern)      = thrown exception
     * StringUtils.throwIfNotMatch("", () -> new Exception(), pattern)        = thrown exception
     * StringUtils.throwIfNotMatch(" ", () -> new Exception(), pattern)       = thrown exception
     * StringUtils.throwIfNotMatch("  bob  ", () -> new Exception(), pattern) = thrown exception
     * StringUtils.throwIfNotMatch("bob", () -> new Exception(), pattern)     = no exception
     * StringUtils.throwIfNotMatch("bob-1", () -> new Exception(), pattern)   = no exception
     *
     * </pre>
     * @param value Value to check, may be null
     * @param exception Supplier of the exception
     * @param <E> Type of exception to throw
     * @param pattern The pattern to match against
     * @throws E The exception when {@code value} does not match the supplied pattern.
     */
    public static <E extends Exception> void throwIfNotMatch(String value, Supplier<E> exception, Pattern pattern)
            throws E {
        if (value == null || !pattern.matcher(value).matches()) {
            throw exception.get();
        }
    }
}
