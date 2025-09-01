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

package uk.gov.dbt.ndtp.federator.utils;

import static io.grpc.Status.fromCodeValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.dbt.ndtp.federator.utils.GRPCExceptionUtils.handleGRPCException;
import static uk.gov.dbt.ndtp.federator.utils.GRPCExceptionUtils.isRetryableException;

import io.grpc.StatusRuntimeException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.dbt.ndtp.federator.exceptions.RetryableException;

public class GRPCExceptionUtilTest {
    private static Stream<Arguments> exceptionCodeList() {
        return Stream.of(
                Arguments.of(0, false),
                Arguments.of(1, true),
                Arguments.of(2, false),
                Arguments.of(3, false),
                Arguments.of(4, true),
                Arguments.of(5, false),
                Arguments.of(6, false),
                Arguments.of(7, true),
                Arguments.of(8, true),
                Arguments.of(9, false),
                Arguments.of(10, true),
                Arguments.of(11, false),
                Arguments.of(12, false),
                Arguments.of(13, false),
                Arguments.of(14, true),
                Arguments.of(15, true),
                Arguments.of(16, true));
    }

    /**
     * The list of GRPC statuses codes are as follows:
     * OK(0),
     * CANCELLED(1),
     * UNKNOWN(2),
     * INVALID_ARGUMENT(3),
     * DEADLINE_EXCEEDED(4),
     * NOT_FOUND(5),
     * ALREADY_EXISTS(6),
     * PERMISSION_DENIED(7),
     * RESOURCE_EXHAUSTED(8),
     * FAILED_PRECONDITION(9),
     * ABORTED(10),
     * OUT_OF_RANGE(11),
     * UNIMPLEMENTED(12),
     * INTERNAL(13),
     * UNAVAILABLE(14),
     * DATA_LOSS(15),
     * UNAUTHENTICATED(16)
     */
    @ParameterizedTest
    @MethodSource("exceptionCodeList")
    public void test_isRetryableException(int code, boolean expected) {
        // given
        // when
        boolean actual = isRetryableException(fromCode(code));
        // then
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("exceptionCodeList")
    public void test_handleGRPCException(int code, boolean retryable) {
        // given
        StatusRuntimeException exception = fromCode(code);
        // when
        // then
        Class expectedExceptionClass = StatusRuntimeException.class;
        if (retryable) {
            expectedExceptionClass = RetryableException.class;
        }
        assertThrows(expectedExceptionClass, () -> handleGRPCException(exception));
    }

    private StatusRuntimeException fromCode(int code) {
        return new StatusRuntimeException(fromCodeValue(code));
    }
}
