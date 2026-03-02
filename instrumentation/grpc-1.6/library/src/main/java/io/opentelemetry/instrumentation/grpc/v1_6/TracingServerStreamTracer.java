/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerStreamTracer;
import io.grpc.Status;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import java.net.SocketAddress;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * A {@link ServerStreamTracer} that detects whether a gRPC server request was handled by the {@link
 * TracingServerInterceptor}. If the interceptor does not fire (unregistered method), {@link
 * #streamClosed(Status)} creates a span for the unhandled request.
 */
final class TracingServerStreamTracer extends ServerStreamTracer {

  static final io.grpc.Context.Key<TracingServerStreamTracer> STREAM_TRACER_KEY =
      io.grpc.Context.key("otel-grpc-stream-tracer");

  private static final String UNKNOWN_METHOD_SPAN_NAME = "_OTHER";

  private final Instrumenter<GrpcRequest, Status> instrumenter;
  private final ContextPropagators propagators;
  private final String fullMethodName;
  private final Metadata headers;
  private final Context parentContext;
  private final Instant startTime;

  private volatile boolean interceptorHandled;
  @Nullable private volatile SocketAddress peerAddress;

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

  void markInterceptorHandled() {
    interceptorHandled = true;
  }

  @Override
  public io.grpc.Context filterContext(io.grpc.Context context) {
    return context.withValue(STREAM_TRACER_KEY, this);
  }

  @Override
  public void serverCallStarted(ServerCall<?, ?> call) {
    if (peerAddress == null) {
      SocketAddress addr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
      if (addr != null) {
        peerAddress = addr;
      }
    }
  }

  @Override
  public void streamClosed(Status status) {
    if (interceptorHandled) {
      return;
    }
    // Interceptor did not fire — this is an unregistered method
    GrpcRequest request = new GrpcRequest(UNKNOWN_METHOD_SPAN_NAME, fullMethodName, headers);
    if (peerAddress != null) {
      request.setPeerSocketAddress(peerAddress);
    }
    // Extract trace context from incoming headers (e.g. W3C traceparent)
    Context extracted =
        propagators
            .getTextMapPropagator()
            .extract(parentContext, request, GrpcRequestGetter.INSTANCE);
    if (instrumenter.shouldStart(extracted, request)) {
      InstrumenterUtil.startAndEnd(
          instrumenter, extracted, request, status, status.getCause(), startTime, Instant.now());
    }
  }
}
