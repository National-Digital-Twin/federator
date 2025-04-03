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

package uk.gov.dbt.ndtp.federator.lifecycle;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ShutdownTask} which shuts down an {@link ExecutorService}.
 * The order of this hook is {@code 100}.
 */
public class ExecutorShutdownTask implements ShutdownTask {

    private static final Duration MINUTE = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(ExecutorShutdownTask.class);

    private final ExecutorService pool;
    private final Duration timeout;

    public ExecutorShutdownTask(ExecutorService pool) {
        this(pool, MINUTE);
    }

    public ExecutorShutdownTask(ExecutorService pool, Duration timeout) {
        this.pool = pool;
        this.timeout = timeout;
    }

    @Override
    public void run() {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn(
                        "Failed to terminate all tasks in the thread pool {} after {}ms, forcefully terminating tasks",
                        pool,
                        timeout.toMillis());
                pool.shutdownNow();
            }
        } catch (InterruptedException ex) {
            // (Re-) Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public int order() {
        return 100;
    }
}
