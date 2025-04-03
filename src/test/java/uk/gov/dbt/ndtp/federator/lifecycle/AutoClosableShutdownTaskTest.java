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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AutoClosableShutdownTaskTest {

    @Test
    void run() {
        AtomicBoolean closed = new AtomicBoolean(false);

        var underTest = new AutoClosableShutdownTask(() -> closed.set(true));

        underTest.run();

        assertTrue(closed.get());
    }

    @Test
    void run_wraps_exceptions() {
        Exception cause = new Exception("Oops");
        var underTest = new AutoClosableShutdownTask(() -> {
            throw cause;
        });

        var exception = Assertions.assertThrows(Exception.class, underTest::run);

        assertSame(cause, exception.getCause());
    }
}
