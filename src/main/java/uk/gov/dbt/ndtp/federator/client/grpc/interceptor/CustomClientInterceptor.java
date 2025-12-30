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

package uk.gov.dbt.ndtp.federator.client.grpc.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomClientInterceptor implements ClientInterceptor {
    public static final Logger LOGGER = LoggerFactory.getLogger("CustomClientInterceptor");
    private long messageCounter = 0L;

    @Override
    public <R, S> ClientCall<R, S> interceptCall(MethodDescriptor<R, S> method, CallOptions options, Channel channel) {
        return new ForwardingClientCall.SimpleForwardingClientCall<R, S>(channel.newCall(method, options)) {

            @Override
            public void start(Listener<S> responseListener, Metadata headers) {
                super.start(
                        new ForwardingClientCallListener.SimpleForwardingClientCallListener<S>(responseListener) {

                            @Override
                            public void onMessage(S message) {
                                if (messageCounter % 500 == 0) {
                                    LOGGER.info("Received response from Server: {}", messageCounter);
                                }
                                ++messageCounter;
                                super.onMessage(message);
                            }
                        },
                        headers);
            }
        };
    }
}
