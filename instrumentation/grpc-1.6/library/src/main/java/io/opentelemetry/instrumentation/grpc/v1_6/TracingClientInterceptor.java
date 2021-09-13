/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.grpc.v1_6.GrpcInjectAdapter.SETTER;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientCall.Listener;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

final class TracingClientInterceptor implements ClientInterceptor {

  @SuppressWarnings("rawtypes")
  private static final AtomicLongFieldUpdater<TracingClientCallListener> MESSAGE_ID_UPDATER =
      AtomicLongFieldUpdater.newUpdater(TracingClientCallListener.class, "messageId");

  private final Instrumenter<GrpcRequest, Status> instrumenter;
  private final ContextPropagators propagators;

  TracingClientInterceptor(
      Instrumenter<GrpcRequest, Status> instrumenter, ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  @Override
  public <REQUEST, RESPONSE> ClientCall<REQUEST, RESPONSE> interceptCall(
      MethodDescriptor<REQUEST, RESPONSE> method, CallOptions callOptions, Channel next) {
    GrpcRequest request = new GrpcRequest(method, null, null);
    Context parentContext = Context.current();
    Context context = instrumenter.start(parentContext, request);
    final ClientCall<REQUEST, RESPONSE> result;
    try (Scope ignored = context.makeCurrent()) {
      try {
        // call other interceptors
        result = next.newCall(method, callOptions);
      } catch (Throwable e) {
        instrumenter.end(context, request, null, e);
        throw e;
      }
    }

    SocketAddress address = result.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
    request.setRemoteAddress(address);

    return new TracingClientCall<>(result, parentContext, context, request);
  }

  final class TracingClientCall<REQUEST, RESPONSE>
      extends ForwardingClientCall.SimpleForwardingClientCall<REQUEST, RESPONSE> {

    private final Context parentContext;
    private final Context context;
    private final GrpcRequest request;

    TracingClientCall(
        ClientCall<REQUEST, RESPONSE> delegate,
        Context parentContext,
        Context context,
        GrpcRequest request) {
      super(delegate);
      this.parentContext = parentContext;
      this.context = context;
      this.request = request;
    }

    @Override
    public void start(Listener<RESPONSE> responseListener, Metadata headers) {
      propagators.getTextMapPropagator().inject(context, headers, SETTER);
      try (Scope ignored = context.makeCurrent()) {
        super.start(
            new TracingClientCallListener<>(responseListener, parentContext, context, request),
            headers);
      } catch (Throwable e) {
        instrumenter.end(context, request, null, e);
        throw e;
      }
    }

    @Override
    public void sendMessage(REQUEST message) {
      try (Scope ignored = context.makeCurrent()) {
        super.sendMessage(message);
      } catch (Throwable e) {
        instrumenter.end(context, request, null, e);
        throw e;
      }
    }
  }

  final class TracingClientCallListener<RESPONSE>
      extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RESPONSE> {

    private final Context parentContext;
    private final Context context;
    private final GrpcRequest request;

    // Used by MESSAGE_ID_UPDATER
    @SuppressWarnings("UnusedVariable")
    volatile long messageId;

    TracingClientCallListener(
        Listener<RESPONSE> delegate, Context parentContext, Context context, GrpcRequest request) {
      super(delegate);
      this.parentContext = parentContext;
      this.context = context;
      this.request = request;
    }

    @Override
    public void onMessage(RESPONSE message) {
      Span span = Span.fromContext(context);
      Attributes attributes =
          Attributes.of(
              GrpcHelper.MESSAGE_TYPE,
              "SENT",
              GrpcHelper.MESSAGE_ID,
              MESSAGE_ID_UPDATER.incrementAndGet(this));
      span.addEvent("message", attributes);
      try (Scope ignored = context.makeCurrent()) {
        delegate().onMessage(message);
      } catch (Throwable e) {
        instrumenter.end(context, request, null, e);
        throw e;
      }
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      instrumenter.end(context, request, status, status.getCause());
      try (Scope ignored = parentContext.makeCurrent()) {
        delegate().onClose(status, trailers);
      }
    }

    @Override
    public void onReady() {
      try (Scope ignored = context.makeCurrent()) {
        delegate().onReady();
      } catch (Throwable e) {
        instrumenter.end(context, request, null, e);
        throw e;
      }
    }
  }
}
