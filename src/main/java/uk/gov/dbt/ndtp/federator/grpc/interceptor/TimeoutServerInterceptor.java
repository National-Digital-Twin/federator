package uk.gov.dbt.ndtp.federator.grpc.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimeoutServerInterceptor implements ServerInterceptor {

    private final long timeoutSeconds;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public TimeoutServerInterceptor(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        Context.CancellableContext timeoutContext =
                Context.current().withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS, scheduler);

        // When deadline is exceeded, close the call
        timeoutContext.addListener(
                context -> {
                    call.close(
                            Status.DEADLINE_EXCEEDED.withDescription("Server-side timeout exceeded"), new Metadata());
                },
                scheduler);

        return Contexts.interceptCall(timeoutContext, call, headers, next);
    }
}
