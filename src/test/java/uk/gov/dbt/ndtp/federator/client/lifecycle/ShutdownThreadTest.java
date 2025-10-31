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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShutdownThreadTest {

    @AfterAll
    static void afterAll() {
        ShutdownThread.reset();
    }

    @BeforeEach
    void setUp() {
        ShutdownThread.reset();
    }

    @Test
    void register_adds_thread_to_runtime_when_not_already_present() throws InterruptedException {
        var task = new TestTask();

        ShutdownThread.register(task);

        runShutdownThread();

        assertTrue(Runtime.getRuntime().removeShutdownHook(ShutdownThread.get()));
        assertTrue(task.ran);
    }

    @Test
    void register_does_not_re_add_thread_to_runtime_when_already_added() {
        ShutdownThread.register(new TestTask());

        Assertions.assertDoesNotThrow(() -> ShutdownThread.register(new TestTask()));

        assertTrue(Runtime.getRuntime().removeShutdownHook(ShutdownThread.get()));
    }

    @Test
    void unregister_removes_thread_from_runtime_if_no_tasks_left() throws InterruptedException {
        var task = new TestTask();

        ShutdownThread.register(task);
        ShutdownThread.unregister(task);

        runShutdownThread();

        assertFalse(Runtime.getRuntime().removeShutdownHook(ShutdownThread.get()));
        assertFalse(task.ran);
    }

    @Test
    void unregister_does_not_remove_thread_from_runtime_if_no_tasks_left() {
        var task = new TestTask();
        var task2 = new TestTask();

        ShutdownThread.register(task);
        ShutdownThread.register(task2);
        ShutdownThread.unregister(task);

        assertTrue(Runtime.getRuntime().removeShutdownHook(ShutdownThread.get()));
    }

    @Test
    void run() throws InterruptedException {
        var task = new TestTask();

        ShutdownThread.register(task);

        runShutdownThread();

        assertTrue(task.ran);
    }

    @Test
    void run_runs_according_to_order() throws InterruptedException {
        var executedTasks = new ArrayList<ShutdownTask>();
        class OrderShutdownTask implements ShutdownTask {

            private final int order;

            OrderShutdownTask(int order) {
                this.order = order;
            }

            @Override
            public void run() {
                executedTasks.add(this);
            }

            @Override
            public int order() {
                return order;
            }
        }
        var first = new OrderShutdownTask(-100);
        var second = new OrderShutdownTask(0);
        var last = new OrderShutdownTask(100);

        ShutdownThread.register(last);
        ShutdownThread.register(first);
        ShutdownThread.register(second);

        runShutdownThread();

        assertEquals(3, executedTasks.size());
        assertEquals(first, executedTasks.get(0));
        assertEquals(second, executedTasks.get(1));
        assertEquals(last, executedTasks.get(2));
    }

    @Test
    void run_does_not_run_tasks_submitted_after_running() throws InterruptedException {
        var task = new TestTask();
        var task2 = new TestTask();

        ShutdownThread.register(task);

        runShutdownThread(() -> {
            Awaitility.await().until(() -> task.ran);
            ShutdownThread.register(task2);
        });

        assertTrue(task.ran);
        assertFalse(task2.ran);
    }

    @Test
    void run_handles_failing_tasks() throws InterruptedException {
        var successfulTask = new TestTask();
        var failingTask = new ShutdownTask() {
            @Override
            public int order() {
                return -1;
            }

            @Override
            public void run() {
                throw new RuntimeException("Oops");
            }
        };

        ShutdownThread.register(failingTask);
        ShutdownThread.register(successfulTask);

        runShutdownThread();

        assertTrue(successfulTask.ran);
    }

    private void runShutdownThread() throws InterruptedException {
        runShutdownThread(() -> {});
    }

    private void runShutdownThread(Runnable intermediate) throws InterruptedException {
        var thread = ShutdownThread.get();
        thread.start();
        intermediate.run();
        thread.join();
    }

    static class TestTask implements ShutdownTask {

        boolean ran = false;

        @Override
        public void run() {
            ran = true;
        }
    }
}
