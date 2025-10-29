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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for handling threads.
 */
public class ThreadUtil {

    //    public static final String SCHEDULER_START_DELAY = "scheduler.start_delay";
    //    public static final String SCHEDULER_POLL_PERIOD = "scheduler.poll_period";
    //    public static final String THIRTY = "30";
    //    public static final String SIXTY = "60";

    public static final Logger LOGGER = LoggerFactory.getLogger("ThreadUtil");

    private ThreadUtil() {}

    public static void awaitShutdown(
            List<Future<?>> futureList, AutoCloseable process, ExecutorService threadExecutor) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down...");
            for (Future<?> future : futureList) {
                try {
                    future.cancel(true);
                } catch (Throwable t) {
                    LOGGER.error("Exception occurred", t);
                }
            }
            try {
                process.close();
                threadExecutor.shutdown();
            } catch (Exception e) {
                LOGGER.info("Exception occurred during shutdown, ignoring.", e);
            }
        }));
        for (Future<?> future : futureList) {
            try {
                future.get();
                LOGGER.info("Future processed: {}", future);
            } catch (Throwable t) {
                LOGGER.error("Error in processing: {}", t.getMessage());
            }
        }
    }

    public static ExecutorService threadExecutor(String threadNamePrefix) {
        return Executors.newCachedThreadPool(new ThreadFactoryWithNamePrefix(threadNamePrefix));
    }

    //    public static ScheduledFuture<?> scheduleTask(String threadNamePrefix, Runnable scheduledTask) {
    //        ScheduledExecutorService executor =
    //                Executors.newSingleThreadScheduledExecutor(new ThreadFactoryWithNamePrefix(threadNamePrefix));
    //        return executor.scheduleAtFixedRate(
    //                scheduledTask,
    //                PropertyUtil.getPropertyLongValue(SCHEDULER_START_DELAY, SIXTY),
    //                PropertyUtil.getPropertyLongValue(SCHEDULER_POLL_PERIOD, THIRTY),
    //                TimeUnit.SECONDS);
    //    }
}
