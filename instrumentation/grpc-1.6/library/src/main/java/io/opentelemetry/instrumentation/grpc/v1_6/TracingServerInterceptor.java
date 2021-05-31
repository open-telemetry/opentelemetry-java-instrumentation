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
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

final class TracingServerInterceptor implements ServerInterceptor {

  @SuppressWarnings("rawtypes")
  private static final AtomicLongFieldUpdater<TracingServerCallListener> MESSAGE_ID_UPDATER =
      AtomicLongFieldUpdater.newUpdater(TracingServerCallListener.class, "messageId");

  private final Instrumenter<GrpcRequest, Status> instrumenter;
  private final boolean captureExperimentalSpanAttributes;

  TracingServerInterceptor(
      Instrumenter<GrpcRequest, Status> instrumenter, boolean captureExperimentalSpanAttributes) {
    this.instrumenter = instrumenter;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  @Override
  public <REQUEST, RESPONSE> ServerCall.Listener<REQUEST> interceptCall(
      ServerCall<REQUEST, RESPONSE> call,
      Metadata headers,
      ServerCallHandler<REQUEST, RESPONSE> next) {
    GrpcRequest request =
        new GrpcRequest(
            call.getMethodDescriptor(),
            headers,
            call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR));
    Context context = instrumenter.start(Context.current(), request);

    try (Scope ignored = context.makeCurrent()) {
      return new TracingServerCallListener<>(
          Contexts.interceptCall(
              io.grpc.Context.current(),
              new TracingServerCall<>(call, context, request),
              headers,
              next),
          context,
          request);
    } catch (Throwable e) {
      instrumenter.end(context, request, null, e);
      throw e;
    }
  }

  final class TracingServerCall<REQUEST, RESPONSE>
      extends ForwardingServerCall.SimpleForwardingServerCall<REQUEST, RESPONSE> {
    private final Context context;
    private final GrpcRequest request;

    TracingServerCall(
        ServerCall<REQUEST, RESPONSE> delegate, Context context, GrpcRequest request) {
      super(delegate);
      this.context = context;
      this.request = request;
    }

    @Override
    public void close(Status status, Metadata trailers) {
      try {
        delegate().close(status, trailers);
      } catch (Throwable e) {
        instrumenter.end(context, request, status, e);
        throw e;
      }
      instrumenter.end(context, request, status, status.getCause());
    }
  }

  final class TracingServerCallListener<REQUEST>
      extends ForwardingServerCallListener.SimpleForwardingServerCallListener<REQUEST> {
    private final Context context;
    private final GrpcRequest request;

    // Used by MESSAGE_ID_UPDATER
    @SuppressWarnings("UnusedVariable")
    volatile long messageId;

    TracingServerCallListener(Listener<REQUEST> delegate, Context context, GrpcRequest request) {
      super(delegate);
      this.context = context;
      this.request = request;
    }

    @Override
    public void onMessage(REQUEST message) {
      // TODO(anuraaga): Restore
      Attributes attributes =
          Attributes.of(
              GrpcHelper.MESSAGE_TYPE,
              "RECEIVED",
              GrpcHelper.MESSAGE_ID,
              MESSAGE_ID_UPDATER.incrementAndGet(this));
      Span.fromContext(context).addEvent("message", attributes);
      delegate().onMessage(message);
    }

    @Override
    public void onHalfClose() {
      try {
        delegate().onHalfClose();
      } catch (Throwable e) {
        instrumenter.end(context, request, null, e);
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
        instrumenter.end(context, request, null, e);
        throw e;
      }
      instrumenter.end(context, request, null, null);
    }

    @Override
    public void onComplete() {
      try {
        delegate().onComplete();
      } catch (Throwable e) {
        instrumenter.end(context, request, null, e);
        throw e;
      }
    }

    @Override
    public void onReady() {
      try {
        delegate().onReady();
      } catch (Throwable e) {
        instrumenter.end(context, request, null, e);
        throw e;
      }
    }
  }
}
