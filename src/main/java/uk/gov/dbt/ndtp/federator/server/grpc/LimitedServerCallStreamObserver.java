package uk.gov.dbt.ndtp.federator.server.grpc;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;

/**
 * Wraps {@link ServerCallStreamObserver} to provide backpressure-aware streaming.
 *
 * <p>Key behaviours:
 * <ul>
 *   <li>Blocks {@link #onNext(Object)} while the gRPC transport is not ready (backpressure).</li>
 *   <li>Unblocks on gRPC {@code onReady} or client cancellation.</li>
 *   <li>Uses a small periodic wake-up as a safety net so we re-check readiness even if a signal is missed.</li>
 *   <li>Fails the RPC if not-ready persists longer than a configured stall timeout.</li>
 *   <li>Makes terminal signalling idempotent to avoid "call already closed" races.</li>
 * </ul>
 *
 * @param <T> stream message type
 */
@Slf4j
public final class LimitedServerCallStreamObserver<T> implements StreamObservable<T> {

    private static final long STALL_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(2);

    /**
     * Safety-net wake interval: we re-check readiness periodically even if no signal arrives.
     * Keep this small enough to avoid "feels stuck", but large enough to avoid busy-waiting.
     */
    private static final long SAFETY_WAKE_NANOS = TimeUnit.MILLISECONDS.toNanos(200);

    /**
     * Only log backpressure if we actually waited at least this long.
     * This avoids noisy logs when readiness flaps quickly.
     */
    private static final long LOG_WAIT_THRESHOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(200);

    private final ServerCallStreamObserver<T> delegate;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition readyOrCancelled = lock.newCondition();

    private final AtomicReference<Runnable> externalOnReady = new AtomicReference<>();
    private final AtomicReference<Runnable> externalOnCancel = new AtomicReference<>();

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean cancelled = false;

    public LimitedServerCallStreamObserver(ServerCallStreamObserver<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");

        // Internal gRPC onReady handler (do not overwrite elsewhere)
        this.delegate.setOnReadyHandler(() -> {
            signalAll();
            Runnable r = externalOnReady.get();
            if (r != null) r.run();
        });

        // Internal gRPC onCancel handler (do not overwrite elsewhere)
        this.delegate.setOnCancelHandler(() -> {
            cancelled = true;
            signalAll();
            Runnable r = externalOnCancel.get();
            if (r != null) r.run();
        });
    }

    @Override
    public void onNext(T value) {
        if (closed.get()) {
            throw Status.CANCELLED.withDescription("gRPC call already closed").asRuntimeException();
        }

        long waitStartedNanos = delegate.isReady() ? 0L : System.nanoTime();

        awaitReadyOrCancelled();

        if (isCancelled() || closed.get()) {
            throw Status.CANCELLED.withDescription("Client cancelled stream").asRuntimeException();
        }

        if (waitStartedNanos != 0L) {
            long waited = System.nanoTime() - waitStartedNanos;
            if (waited >= LOG_WAIT_THRESHOLD_NANOS) {
                log.trace("SERVER BACKPRESSURE — waited {} ms, resuming", TimeUnit.NANOSECONDS.toMillis(waited));
            }
        }

        delegate.onNext(value);
    }

    /**
     * Waits until the stream becomes ready, the client cancels, or the stall timeout elapses.
     *
     * <p>We primarily rely on gRPC onReady/onCancel signalling. A small periodic wake-up is used as
     * a safety net so we re-check readiness even if a signal is missed or delayed.</p>
     */
    private void awaitReadyOrCancelled() {
        if ((delegate.isReady() && !isCancelled()) || closed.get()) return;

        final long start = System.nanoTime();

        lock.lock();
        try {
            while (!closed.get() && !isCancelled() && !delegate.isReady()) {
                long elapsed = System.nanoTime() - start;
                long remainingToStall = STALL_TIMEOUT_NANOS - elapsed;

                if (remainingToStall <= 0) {
                    log.warn(
                            "gRPC stream stalled >2m (ready={}, cancelled={}, closed={})",
                            delegate.isReady(),
                            isCancelled(),
                            closed.get());
                    throw Status.DEADLINE_EXCEEDED
                            .withDescription("gRPC stream stalled (not-ready > 2 minutes)")
                            .asRuntimeException();
                }

                long waitNanos = Math.min(remainingToStall, SAFETY_WAKE_NANOS);

                // Use the return value: if <= 0, we woke because the timeout slice elapsed (not a signal).
                long remainingInSlice = readyOrCancelled.awaitNanos(waitNanos);
                if (remainingInSlice <= 0) {
                    // no-op: this was the safety wake timeout, loop will re-check readiness/cancel/timeout
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw Status.CANCELLED
                    .withDescription("Interrupted while waiting for gRPC readiness")
                    .withCause(ie)
                    .asRuntimeException();
        } finally {
            lock.unlock();
        }
    }

    private void signalAll() {
        lock.lock();
        try {
            readyOrCancelled.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setOnCancelHandler(Runnable onCancelHandler) {
        externalOnCancel.set(onCancelHandler);
    }

    @Override
    public boolean isCancelled() {
        return cancelled || delegate.isCancelled();
    }

    @Override
    public void onError(Exception e) {
        if (!closed.compareAndSet(false, true)) return;
        cancelled = true;
        signalAll();
        delegate.onError(e);
    }

    @Override
    public void onCompleted() {
        if (!closed.compareAndSet(false, true)) return;
        cancelled = true;
        signalAll();
        delegate.onCompleted();
    }

    @Override
    public void setOnReadyHandler(Runnable onReadyHandler) {
        externalOnReady.set(onReadyHandler);
    }
}
