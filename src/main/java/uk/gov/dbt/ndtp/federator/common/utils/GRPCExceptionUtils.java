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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.exceptions.RetryableException;

/**
 * Util class for processing GRPC Exceptions
 */
public class GRPCExceptionUtils {
    public static final Logger LOGGER = LoggerFactory.getLogger("GRPCExceptionUtils");
    private static final List<Status.Code> RETRYABLE_STATUSES = List.of(
            Status.Code.UNAVAILABLE,
            Status.Code.DEADLINE_EXCEEDED,
            Status.Code.RESOURCE_EXHAUSTED,
            Status.Code.DATA_LOSS,
            Status.Code.CANCELLED,
            Status.Code.ABORTED,
            Status.Code.PERMISSION_DENIED,
            Status.Code.UNAUTHENTICATED);

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
    private GRPCExceptionUtils() {}

    /**
     * Indicates whether we deem the given exception to be retryable.
     * For example an UNAVAILABLE status indicates that the relevant server
     * is down, and we should continue to retry (after back-off).
     * Alternatively, if we receive an UNAUTHENTICATED then we are using
     * the wrong credentials and therefore retrying would be pointless.
     *
     * @return true if we deem the GRPC exception something to retry
     */
    public static boolean isRetryableException(StatusRuntimeException exception) {
        return RETRYABLE_STATUSES.contains(exception.getStatus().getCode());
    }

    public static void handleGRPCException(StatusRuntimeException exception) {
        LOGGER.debug("Exception is {}", exception.getStatus().getCode());
        if (isRetryableException(exception)) {
            LOGGER.debug("RetryableException thrown");
            throw new RetryableException(exception);
        } else {
            LOGGER.debug("Standard Exception thrown");
            throw exception;
        }
    }
}
