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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.dbt.ndtp.federator.common.utils.ThreadUtil.awaitShutdown;
import static uk.gov.dbt.ndtp.federator.common.utils.ThreadUtil.threadExecutor;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

public class ThreadUtilTest {

    private static final boolean STOP = false;

    private static final String RANDOM_THREAD_NAME = RandomStringUtils.random(6, true, false);

    @Test
    public void test_threadExecutor_namesThreads() {
        // given
        ExecutorService service = threadExecutor(RANDOM_THREAD_NAME);
        // when
        String threadName = ((ThreadPoolExecutor) service)
                .getThreadFactory()
                .newThread(this::count)
                .getName();
        // then
        assertTrue(threadName.startsWith(RANDOM_THREAD_NAME));
    }

    @Test
    public void test_awaitShutdown_failingTask() {
        // given
        ExecutorService service = threadExecutor(RANDOM_THREAD_NAME);
        TestCloseable closeableProcess = new TestCloseable();

        Future<?> f = service.submit(this::waitForChangeThatNeverComes);

        // when
        awaitShutdown(Collections.singletonList(f), closeableProcess, service);

        // then
        assertTrue(f.isDone());
        assertFalse(closeableProcess.getClosed());
    }

    @Test
    public void test_awaitShutdown_succeedingTask() {
        // given
        ExecutorService service = threadExecutor(RANDOM_THREAD_NAME);
        TestCloseable closeableProcess = new TestCloseable();

        Future<?> f = service.submit(this::waitForChangeThatArrives);

        // when
        awaitShutdown(Collections.singletonList(f), closeableProcess, service);

        // then
        assertTrue(f.isDone());
        assertFalse(closeableProcess.getClosed());
    }

    //    @Test
    //    public void test_awaitShutdown_ignoreException() {
    //        // given
    //        ExecutorService service = threadExecutor(RANDOM_THREAD_NAME);
    //        ExceptionCloseable exceptionCloseable = new ExceptionCloseable();
    //        Future<?> f = service.submit(this::waitForChangeThatArrives);
    //
    //        // when
    //        // then
    //        assertDoesNotThrow(() -> awaitShutdown(Collections.singletonList(f), exceptionCloseable, service));
    //        assertTrue(f.isDone());
    //    }

    @Test
    public void test_awaitShutdown_ignoreExceptions() {
        // given
        ExecutorService service = threadExecutor(RANDOM_THREAD_NAME);
        ExceptionCloseable exceptionCloseable = new ExceptionCloseable();
        Future<?> f = new ExceptionalFuture();

        // when
        // then
        assertDoesNotThrow(() -> awaitShutdown(Collections.singletonList(f), exceptionCloseable, service));
    }

    private void count() {
        for (int i = 0; i < 100; ) {
            i += 1;
        }
    }

    private static class TestCloseable implements AutoCloseable {
        private boolean closed = false;

        @Override
        public void close() {
            closed = true;
        }

        public boolean getClosed() {
            return closed;
        }
    }

    private static class ExceptionCloseable implements AutoCloseable {

        @Override
        public void close() {
            throw new RuntimeException("TEST");
        }
    }

    private static class ExceptionalFuture implements Future {

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new RuntimeException("TEST");
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }

    private void waitForChangeThatNeverComes() {
        await().pollInterval(1, TimeUnit.MILLISECONDS)
                .atMost(2, TimeUnit.MILLISECONDS)
                .until(() -> STOP);
    }

    private void waitForChangeThatArrives() {
        await().pollInterval(2, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.MILLISECONDS)
                .until(() -> !STOP);
    }
}
