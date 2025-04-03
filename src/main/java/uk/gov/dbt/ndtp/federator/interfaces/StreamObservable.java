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

package uk.gov.dbt.ndtp.federator.interfaces;

import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;

/**
 * A StreamObservable allows data to be sent back to the caller, handles cancellation and errors.
 */
public interface StreamObservable {

    /**
     * Blocks until the next message is ready to be processed.
     *
     * @param value is the Kafka byte data from the message
     */
    void onNext(KafkaByteBatch value);

    /**
     * Sets the function that is called when the interaction is cancelled.
     *
     * @param onCancelHandler is the function that will run when cancel is called.
     */
    void setOnCancelHandler(Runnable onCancelHandler);

    /**
     * Has cancel been called?
     *
     * @return is true if cancel has been called at the client side.
     */
    boolean isCancelled();

    /**
     * If there is an error the exception can be passed back up to the caller.
     *
     * @param e the exception that happened
     */
    void onError(Exception e);

    /**
     * Called once all the messages have been sent and the connections can be
     * closed.
     */
    void onCompleted();
}
