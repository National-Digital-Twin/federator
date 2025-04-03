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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.concurrent.AutoLock;

// TODO: Hopefully uses of ThreadUtil#awaitShutdown can be replaced with registering tasks here eventually
public final class ShutdownThread extends Thread {

    private static final Logger log = LoggerFactory.getLogger(ShutdownThread.class);

    private static ShutdownThread _this = new ShutdownThread();

    private final Collection<ShutdownTask> tasks = Collections.synchronizedList(new ArrayList<>());
    private final AutoLock lock = new AutoLock();

    private boolean hooked = false;

    private ShutdownThread() {
        super("ShutdownThread");
    }

    // Synchronised due to only being called in methods that have already acquired a lock
    private void hook() {
        try {
            if (!hooked) {
                Runtime.getRuntime().addShutdownHook(this);
                hooked = true;
            }
        } catch (Exception e) {
            log.debug("Ignoring exception from hooking", e);
            log.info("Shutdown in progress");
        }
    }

    // Synchronised due to only being called in methods that have already acquired a lock
    private void unhook() {
        try {
            if (hooked) {
                hooked = false;
                Runtime.getRuntime().removeShutdownHook(this);
            }
        } catch (Exception e) {
            log.debug("Ignoring exception from unhooking", e);
            log.debug("Shutdown in progress");
        }
    }

    public static void register(ShutdownTask task) {
        try (AutoLock ignored = _this.lock.lock()) {
            log.debug("Registering a shutdown task {}", task);
            _this.tasks.add(task);
            _this.hook();
        }
    }

    public static void unregister(ShutdownTask task) {
        try (AutoLock ignored = _this.lock.lock()) {
            log.debug("Unregistering a shutdown task {}", task);
            _this.tasks.remove(task);
            if (_this.tasks.isEmpty()) {
                _this.unhook();
            }
        }
    }

    @Override
    public void run() {
        log.debug("Starting shutdown tasks");
        List<ShutdownTask> tasks;
        try (AutoLock ignored = _this.lock.lock()) {
            tasks = new ArrayList<>(List.copyOf(this.tasks));
            this.tasks.clear();
        }
        tasks.sort(Comparator.comparingInt(ShutdownTask::order));
        for (ShutdownTask task : tasks) {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("Unable to run a shutdown hook, ignoring", e);
            }
        }
    }

    /**
     * For test purposes only
     */
    static ShutdownThread get() {
        return _this;
    }

    /**
     * For test purposes only
     */
    static void reset() {
        if (_this.hooked) {
            _this.unhook();
        }
        _this = new ShutdownThread();
    }
}
