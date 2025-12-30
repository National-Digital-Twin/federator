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

package uk.gov.dbt.ndtp.federator.client.lifecycle;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CancelableFutureShutdownTaskTest {

    private static ExecutorService pool;

    @BeforeAll
    static void beforeAll() {
        pool = Executors.newSingleThreadExecutor();
    }

    @AfterAll
    static void afterAll() {
        pool.shutdown();
    }

    @Test
    void run() {
        Future<?> task =
                pool.submit(() -> await().atMost(Duration.ofSeconds(10)).untilTrue(new AtomicBoolean(false)));
        CancelableFutureShutdownTask shutdownTask = new CancelableFutureShutdownTask(task);

        shutdownTask.run();

        assertTrue(task.isCancelled());
    }

    @Test
    void run_with_done_task() {
        Future<?> task = pool.submit(() -> {});
        CancelableFutureShutdownTask shutdownTask = new CancelableFutureShutdownTask(task);

        await().until(task::isDone);

        assertDoesNotThrow(shutdownTask::run);
        assertTrue(task.isDone());
    }
}
