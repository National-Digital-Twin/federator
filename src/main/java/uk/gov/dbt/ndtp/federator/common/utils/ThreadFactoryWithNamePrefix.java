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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadFactory with the ability to set the thread name prefix.
 * This class is exactly similar to
 * {@link java.util.concurrent.Executors#defaultThreadFactory()}
 * from JDK8, except for the thread naming feature.
 *
 * <p>
 * The factory creates threads that have names on the form
 * <i>prefix-N-thread-M</i>, where <i>prefix</i>
 * is a string provided in the constructor, <i>N</i> is the sequence number of
 * this factory, and <i>M</i> is the sequence number of the thread created
 * by this factory.
 */
public class ThreadFactoryWithNamePrefix implements ThreadFactory {

    private static final AtomicInteger poolNumber = new AtomicInteger(1);
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    /**
     * Creates a new ThreadFactory where threads are created with a name prefix
     * of <code>prefix</code>.
     *
     * @param prefix Thread name prefix. Never use a value of "pool" as in that
     *      case you might as well have used
     *      {@link java.util.concurrent.Executors#defaultThreadFactory()}.
     */
    public ThreadFactoryWithNamePrefix(String prefix) {
        namePrefix = prefix + "-" + poolNumber.getAndIncrement() + "-thread-";
    }

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, namePrefix + threadNumber.getAndIncrement());
    }
}
