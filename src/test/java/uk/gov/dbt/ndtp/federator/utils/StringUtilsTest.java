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

package uk.gov.dbt.ndtp.federator.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class StringUtilsTest {

    private static final Pattern SERVER_NAME_REGEX = Pattern.compile("^[a-zA-Z0-9]+$");
    private final RuntimeException expected = new RuntimeException("oops");

    @ParameterizedTest
    @MethodSource("blank")
    void throwIfBlank_throws_when_blank(String value) {
        var actual = assertThrows(RuntimeException.class, () -> StringUtils.throwIfBlank(value, () -> expected));

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bob", "  bob  "})
    void throwIfBlank_does_not_throw_when_not_blank(String value) {
        assertDoesNotThrow(() -> StringUtils.throwIfBlank(value, () -> expected));
    }

    @ParameterizedTest
    @MethodSource("blank")
    void throwIfNotMatch_throws_when_blank(String value) {
        var actual = assertThrows(
                RuntimeException.class, () -> StringUtils.throwIfNotMatch(value, () -> expected, SERVER_NAME_REGEX));

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  bob  ", "b&b"})
    void throwIfNotMatch_throws_when_server_name_not_alphanumeric(String value) {
        var actual = assertThrows(
                RuntimeException.class, () -> StringUtils.throwIfNotMatch(value, () -> expected, SERVER_NAME_REGEX));

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bob", "bob1"})
    void throwIfNotMatch_when_alphanumeric_with_dash(String value) {
        assertDoesNotThrow(() -> StringUtils.throwIfNotMatch(value, () -> expected, SERVER_NAME_REGEX));
    }

    static Stream<Arguments> blank() {
        return Stream.of(
                Arguments.arguments(Named.named("null", null)),
                Arguments.arguments(Named.named("empty", "")),
                Arguments.arguments(Named.named("blank", " ")));
    }
}
