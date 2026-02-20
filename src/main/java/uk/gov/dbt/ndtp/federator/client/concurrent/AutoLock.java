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

package uk.gov.dbt.ndtp.federator.client.concurrent;

import java.util.concurrent.locks.ReentrantLock;

/**
 * An auto-closable re-entrant lock that allows for use with a try with resources.
 */
public class AutoLock implements AutoCloseable {
    private final ReentrantLock lock;

    public AutoLock() {
        lock = new ReentrantLock();
    }

    /**
     * <p>Acquires the lock.</p>
     *
     * @return this self to allow for use within try with resources
     */
    public AutoLock lock() {
        lock.lock();
        return this;
    }

    /**
     * @see ReentrantLock#unlock()
     */
    @Override
    public void close() {
        lock.unlock();
    }
}
