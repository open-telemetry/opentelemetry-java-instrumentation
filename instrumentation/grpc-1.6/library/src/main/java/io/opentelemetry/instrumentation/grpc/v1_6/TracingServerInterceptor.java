/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;

final class TracingServerInterceptor implements ServerInterceptor {

  private final GrpcServerTracer tracer;
  private final boolean captureExperimentalSpanAttributes;

  TracingServerInterceptor(GrpcServerTracer tracer, boolean captureExperimentalSpanAttributes) {
    this.tracer = tracer;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  @Override
  public <REQUEST, RESPONSE> ServerCall.Listener<REQUEST> interceptCall(
      ServerCall<REQUEST, RESPONSE> call,
      Metadata headers,
      ServerCallHandler<REQUEST, RESPONSE> next) {

    String methodName = call.getMethodDescriptor().getFullMethodName();
    Context context = tracer.startSpan(methodName, headers);
    Span span = Span.fromContext(context);

    SocketAddress address = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
    if (address instanceof InetSocketAddress) {
      InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
      span.setAttribute(SemanticAttributes.NET_PEER_PORT, inetSocketAddress.getPort());
      span.setAttribute(
          SemanticAttributes.NET_PEER_IP, inetSocketAddress.getAddress().getHostAddress());
    }
    GrpcHelper.prepareSpan(span, methodName);

    try (Scope ignored = context.makeCurrent()) {
      return new TracingServerCallListener<>(
          Contexts.interceptCall(
              io.grpc.Context.current(), new TracingServerCall<>(call, context), headers, next),
          context);
    } catch (Throwable e) {
      tracer.endExceptionally(context, e);
      throw e;
    }
  }

  final class TracingServerCall<REQUEST, RESPONSE>
      extends ForwardingServerCall.SimpleForwardingServerCall<REQUEST, RESPONSE> {
    private final Context context;

    TracingServerCall(ServerCall<REQUEST, RESPONSE> delegate, Context context) {
      super(delegate);
      this.context = context;
    }

    @Override
    public void close(Status status, Metadata trailers) {
      tracer.setStatus(context, status);
      try {
        delegate().close(status, trailers);
      } catch (Throwable e) {
        tracer.endExceptionally(context, e);
        throw e;
      }
    }
  }

  final class TracingServerCallListener<REQUEST>
      extends ForwardingServerCallListener.SimpleForwardingServerCallListener<REQUEST> {
    private final Context context;

    private final AtomicLong messageId = new AtomicLong();

    TracingServerCallListener(Listener<REQUEST> delegate, Context context) {
      super(delegate);
      this.context = context;
    }

    @Override
    public void onMessage(REQUEST message) {
      // TODO(anuraaga): Restore
      Attributes attributes =
          Attributes.of(
              GrpcHelper.MESSAGE_TYPE,
              "RECEIVED",
              GrpcHelper.MESSAGE_ID,
              messageId.incrementAndGet());
      Span.fromContext(context).addEvent("message", attributes);
      delegate().onMessage(message);
    }

    @Override
    public void onHalfClose() {
      try {
        delegate().onHalfClose();
      } catch (Throwable e) {
        tracer.endExceptionally(context, e);
        throw e;
      }
    }

    @Override
    public void onCancel() {
      try {
        delegate().onCancel();
        if (captureExperimentalSpanAttributes) {
          Span.fromContext(context).setAttribute("grpc.canceled", true);
        }
      } catch (Throwable e) {
        tracer.endExceptionally(context, e);
        throw e;
      }
      tracer.end(context);
    }

    @Override
    public void onComplete() {
      try {
        delegate().onComplete();
      } catch (Throwable e) {
        tracer.endExceptionally(context, e);
        throw e;
      }
      tracer.end(context);
    }

    @Override
    public void onReady() {
      try {
        delegate().onReady();
      } catch (Throwable e) {
        tracer.endExceptionally(context, e);
        throw e;
      }
    }
  }
}
