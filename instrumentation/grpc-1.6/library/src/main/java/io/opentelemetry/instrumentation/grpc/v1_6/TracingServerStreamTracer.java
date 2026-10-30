/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerStreamTracer;
import io.grpc.Status;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link ServerStreamTracer} that creates spans for requests to unregistered gRPC methods, which
 * server interceptors do not see.
 */
final class TracingServerStreamTracer extends ServerStreamTracer {

  private static final String UNKNOWN_METHOD_NAME = "_OTHER";
  private static final io.grpc.Context.Key<AtomicBoolean> STREAM_TRACER_STATE_KEY =
      io.grpc.Context.key("otel-grpc-stream-tracer-state");

  private final Instrumenter<GrpcRequest, Status> instrumenter;
  private final ContextPropagators propagators;
  private final String fullMethodName;
  private final Metadata headers;
  private final Context parentContext;
  private final Instant startTime;

  private volatile AtomicBoolean handledOrSpanStarted = new AtomicBoolean();

  TracingServerStreamTracer(
      Instrumenter<GrpcRequest, Status> instrumenter,
      ContextPropagators propagators,
      String fullMethodName,
      Metadata headers,
      Context parentContext) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
    this.fullMethodName = fullMethodName;
    this.headers = headers;
    this.parentContext = parentContext;
    this.startTime = Instant.now();
  }

  @Override
  public io.grpc.Context filterContext(io.grpc.Context context) {
    AtomicBoolean state = STREAM_TRACER_STATE_KEY.get(context);
    if (state == null) {
      return context.withValue(STREAM_TRACER_STATE_KEY, handledOrSpanStarted);
    }
    handledOrSpanStarted = state;
    return context;
  }

  @Override
  public void serverCallStarted(ServerCall<?, ?> call) {
    // gRPC creates a ServerCall only after resolving a registered method. Mark it handled here in
    // case another interceptor short-circuits the call before the tracing interceptor runs.
    handledOrSpanStarted.set(true);
  }

  @Override
  public void streamClosed(Status status) {
    // A ServerCall starts only for a method that is registered on the server, and gRPC closes a
    // stream whose method it could not find with a specific UNIMPLEMENTED status. Requiring both
    // keeps other failures that occur before the ServerCall is created from being reported here.
    if (!isUnknownMethod(status) || handledOrSpanStarted.get()) {
      return;
    }
    GrpcRequest request = new GrpcRequest(UNKNOWN_METHOD_NAME, fullMethodName, headers);
    Context extracted =
        propagators
            .getTextMapPropagator()
            .extract(parentContext, request, GrpcRequestGetter.INSTANCE);
    if (instrumenter.shouldStart(extracted, request)
        && handledOrSpanStarted.compareAndSet(false, true)) {
      InstrumenterUtil.startAndEnd(
          instrumenter, extracted, request, status, status.getCause(), startTime, Instant.now());
    }
  }

  private boolean isUnknownMethod(Status status) {
    return status.getCode() == Status.Code.UNIMPLEMENTED
        && ("Method not found: " + fullMethodName).equals(status.getDescription());
  }
}
