// filepath:
// /home/vagrant/development/workspace/federator/src/test/java/uk/gov/dbt/ndtp/federator/client/lifecycle/AutoClosableShutdownTaskTest.java
// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

package uk.gov.dbt.ndtp.federator.client.lifecycle;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
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
        UncheckedIOException cause = new UncheckedIOException(new IOException("Oops"));
        var underTest = new AutoClosableShutdownTask(() -> {
            throw cause;
        });

        var exception = Assertions.assertThrows(UncheckedIOException.class, underTest::run);

        // The implementation wraps any Exception in a new UncheckedIOException whose cause is an IOException
        // that in turn has the original exception as its cause. Verify the original exception is in the cause chain.
        assertSame(cause, exception.getCause().getCause());
    }
}
