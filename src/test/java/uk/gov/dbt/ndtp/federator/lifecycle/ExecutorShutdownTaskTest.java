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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class ExecutorShutdownTaskTest {

    private final ExecutorService service = Executors.newCachedThreadPool();

    @Test
    void shuts_down_gracefully() {
        ExecutorShutdownTask underTest = new ExecutorShutdownTask(service);

        assertDoesNotThrow(underTest::run);
    }

    @Test
    void shuts_down_gracefully_when_tasks_running() {
        ExecutorShutdownTask underTest = new ExecutorShutdownTask(service);

        AtomicBoolean complete = new AtomicBoolean(false);

        Future<?> job = service.submit(() -> {
            try {
                Awaitility.await().atMost(Duration.ofMillis(100)).untilTrue(new AtomicBoolean(false));
            } finally {
                complete.set(true);
            }
        });

        underTest.run();

        Awaitility.await().untilTrue(complete);

        assertTrue(job.isDone());
    }

    @Test
    void shuts_down_gracefully_when_tasks_running_longer_than_allowed() {
        ExecutorShutdownTask underTest = new ExecutorShutdownTask(service, Duration.ofMillis(10));
        AtomicBoolean complete = new AtomicBoolean(false);

        Future<?> job = service.submit(() -> {
            try {
                Awaitility.await().atMost(Duration.ofMillis(100)).untilTrue(new AtomicBoolean(false));
            } finally {
                complete.set(true);
            }
        });

        underTest.run();

        Awaitility.await().untilTrue(complete);

        assertTrue(job.isDone());
    }

    @Test
    void order_is_greater_than_normal_task() {
        ExecutorShutdownTask underTest = new ExecutorShutdownTask(service);

        assertTrue(underTest.order() > ((ShutdownTask) () -> {}).order());
    }
}
