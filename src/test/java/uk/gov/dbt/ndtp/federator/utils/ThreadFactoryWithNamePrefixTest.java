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

package uk.gov.dbt.ndtp.federator.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThreadFactoryWithNamePrefixTest {

    private static class TestThread extends Thread {
        final String prefix;
        final int priority;
        AssertionError error = null;

        public TestThread(String prefix, int priority) {
            this.prefix = prefix;
            this.priority = priority;
        }

        public void run() {
            currentThread().setPriority(priority);

            try {
                test_newThread_inheritsCurrentProperties();
            } catch (AssertionError e) {
                error = e;
            }
        }

        private void test_newThread_inheritsCurrentProperties() {
            // given
            ThreadFactoryWithNamePrefix cut = new ThreadFactoryWithNamePrefix(prefix);

            // when
            Thread t = cut.newThread(() -> {});

            // then
            assertFalse(t.isDaemon());
            assertEquals(priority, t.getPriority(), "The priority of the thread was not inherited");
        }
    }

    @ParameterizedTest
    @CsvSource({"LowPriority, 1", "MaxPriority, 10", "NormPriority, 5"})
    void test_newThread_doesNotInheritPriorityOrDaemonStatus(String threadName, int priority)
            throws InterruptedException {
        TestThread testThread = new TestThread(threadName, priority);
        testThread.start();
        testThread.join();
        if (testThread.error != null) {
            throw testThread.error;
        }
        assertEquals(priority, testThread.getPriority());
        assertFalse(testThread.isDaemon());
    }
}
