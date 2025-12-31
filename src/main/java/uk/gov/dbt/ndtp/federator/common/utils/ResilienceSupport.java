/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.utils;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.core.IntervalFunction;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Math.min;

/**
 * Centralized Resilience4j configuration and decoration helpers.
 * Uses PropertyUtil for configuration under prefix: management.node.resilience.*
 */
public final class ResilienceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ResilienceSupport.class);

    private static final AtomicReference<RetryRegistry> retryRegistry = new AtomicReference<>();
    private static final AtomicReference<CircuitBreakerRegistry> circuitBreakerRegistry = new AtomicReference<>();

    private ResilienceSupport() {}

    private static RetryRegistry getRetryRegistry() {
        return retryRegistry.updateAndGet(current -> current != null ? current : RetryRegistry.of(buildRetryConfig()));
    }

    private static CircuitBreakerRegistry getCircuitBreakerRegistry() {
        return circuitBreakerRegistry.updateAndGet(current -> current != null ? current : CircuitBreakerRegistry.of(buildCircuitBreakerConfig()));
    }

    private static RetryConfig buildRetryConfig() {
        int maxAttempts = parseInt("management.node.resilience.retry.maxAttempts", 10);
        // Requirement: 5 attempts within 5 minutes. Some versions may not support maxDuration; we always enforce attempts.
        // Exponential backoff with a cap at 5 minutes between attempts
        Duration maxBackoff = parseDuration("management.node.resilience.retry.maxBackoff", Duration.ofMinutes(15));
        Duration initialWait = parseDuration("management.node.resilience.retry.initialWait", Duration.ofMillis(200));
        String retryOnClasses = PropertyUtil.getPropertyValue("management.node.resilience.retry.retryOn", "java.lang.RuntimeException");

        IntervalFunction intervalFn = getIntervalFunction(initialWait, maxBackoff);

        RetryConfig.Builder<Object> builder = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(intervalFn);

        // Configure which exception classes are retryable
        Class<?>[] retryables = parseExceptionClasses(retryOnClasses);
        if (retryables.length > 0) {
            // Safe cast: Resilience4j expects Class<? extends Throwable>[]
            @SuppressWarnings("unchecked")
            Class<? extends Throwable>[] throwableClasses = (Class<? extends Throwable>[]) retryables;
             builder.retryExceptions(throwableClasses);
        } else {
                builder.retryExceptions(RuntimeException.class);
        }

        return builder.build();
    }

    private static @NonNull IntervalFunction getIntervalFunction(Duration initialWait, Duration maxBackoff) {
    return attempt -> {
      // attempt starts at 1 for first retry
      long base = initialWait.toMillis();
      long max = maxBackoff.toMillis();
      long factor = 1L << Math.clamp(attempt - 1L, 0L, 30L);
      long next = min(base * factor, max);
      LOG.info(
          "Resilience retry: attempt {} will wait {} ms before next try (cap {} ms)",
          attempt,
          next,
          max);
      return next;
    };
    }

    private static CircuitBreakerConfig buildCircuitBreakerConfig() {
        float failureRateThreshold = parseFloat("management.node.resilience.circuitBreaker.failureRateThreshold", 50.0f);
        int slidingWindowSize = parseInt("management.node.resilience.circuitBreaker.slidingWindowSize", 10);
        int minimumNumberOfCalls = parseInt("management.node.resilience.circuitBreaker.minimumNumberOfCalls", 20);
        int permittedHalfOpen = parseInt("management.node.resilience.circuitBreaker.permittedNumberOfCallsInHalfOpenState", 1);
        Duration waitOpen = parseDuration("management.node.resilience.circuitBreaker.waitDurationInOpenState", Duration.ofSeconds(60));

        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .permittedNumberOfCallsInHalfOpenState(permittedHalfOpen)
                .waitDurationInOpenState(waitOpen);
        return builder.build();
    }

    private static int parseInt(String key, int def) {
        String v = PropertyUtil.getPropertyValue(key, String.valueOf(def));
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return def; }
    }

    private static float parseFloat(String key, float def) {
        String v = PropertyUtil.getPropertyValue(key, String.valueOf(def));
        try { return Float.parseFloat(v.trim()); } catch (Exception e) { return def; }
    }

    private static Duration parseDuration(String key, Duration def) {
        String v = PropertyUtil.getPropertyValue(key, def.toString());
        try { return Duration.parse(v.trim()); } catch (Exception e) { return def; }
    }

    public static Retry getRetry(String name) {
        Objects.requireNonNull(name);
        return getRetryRegistry().retry(name);
    }

    public static CircuitBreaker getCircuitBreaker(String name) {
        Objects.requireNonNull(name);
        return getCircuitBreakerRegistry().circuitBreaker(name);
    }

    /**
     * Testing helper to clear registries so tests can reconfigure policies per test.
     */
    public static void clearForTests() {
        retryRegistry.set(null);
        circuitBreakerRegistry.set(null);
    }

    public static <T> T decorateAndExecute(String componentName, Supplier<T> supplier) {
        Retry retry = getRetry(componentName);
        CircuitBreaker circuitBreaker = getCircuitBreaker(componentName);

        Supplier<T> withCb = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        Supplier<T> withRetry = Retry.decorateSupplier(retry, withCb);
        return withRetry.get();
    }

    private static Class<?>[] parseExceptionClasses(String csv) {
        if (csv == null || csv.trim().isEmpty()) return new Class<?>[0];
        String[] parts = csv.split(",");
        java.util.List<Class<?>> classes = new java.util.ArrayList<>();
        for (String p : parts) {
            String name = p.trim();
            if (name.isEmpty()) continue;
            try {
                Class<?> cls = Class.forName(name);
                if (Throwable.class.isAssignableFrom(cls)) {
                    classes.add(cls);
                } else {
                    LOG.warn("Configured retry class {} is not a Throwable and will be ignored", name);
                }
            } catch (ClassNotFoundException e) {
                LOG.warn("Configured retry class {} not found; ignoring", name);
            }
        }
        return classes.toArray(new Class<?>[0]);
    }
}
