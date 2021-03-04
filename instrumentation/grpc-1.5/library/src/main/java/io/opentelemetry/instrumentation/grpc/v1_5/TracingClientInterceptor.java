/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5;

import static io.opentelemetry.instrumentation.grpc.v1_5.GrpcInjectAdapter.SETTER;

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
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;

final class TracingClientInterceptor implements ClientInterceptor {

  private final GrpcClientTracer tracer;

  TracingClientInterceptor(GrpcClientTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public <REQUEST, RESPONSE> ClientCall<REQUEST, RESPONSE> interceptCall(
      MethodDescriptor<REQUEST, RESPONSE> method, CallOptions callOptions, Channel next) {
    String methodName = method.getFullMethodName();
    Context context = tracer.startSpan(methodName);
    Span span = Span.fromContext(context);
    GrpcHelper.prepareSpan(span, methodName);
    final ClientCall<REQUEST, RESPONSE> result;
    try (Scope ignored = context.makeCurrent()) {
      try {
        // call other interceptors
        result = next.newCall(method, callOptions);
      } catch (Throwable e) {
        tracer.endExceptionally(context, e);
        throw e;
      }
    }

    SocketAddress address = result.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
    if (address instanceof InetSocketAddress) {
      InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
      NetPeerUtils.INSTANCE.setNetPeer(span, inetSocketAddress);
    }

    return new TracingClientCall<>(result, span, context);
  }

  final class TracingClientCall<REQUEST, RESPONSE>
      extends ForwardingClientCall.SimpleForwardingClientCall<REQUEST, RESPONSE> {

    private final Span span;
    private final Context context;

    TracingClientCall(ClientCall<REQUEST, RESPONSE> delegate, Span span, Context context) {
      super(delegate);
      this.span = span;
      this.context = context;
    }

    @Override
    public void start(Listener<RESPONSE> responseListener, Metadata headers) {
      tracer.inject(context, headers, SETTER);
      try (Scope ignored = context.makeCurrent()) {
        super.start(new TracingClientCallListener<>(responseListener, context), headers);
      } catch (Throwable e) {
        tracer.endExceptionally(context, e);
        throw e;
      }
    }

    @Override
    public void sendMessage(REQUEST message) {
      try (Scope ignored = context.makeCurrent()) {
        super.sendMessage(message);
      } catch (Throwable e) {
        tracer.endExceptionally(context, e);
        throw e;
      }
    }
  }

  final class TracingClientCallListener<RESPONSE>
      extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RESPONSE> {
    private final Context context;

    private final AtomicLong messageId = new AtomicLong();

    TracingClientCallListener(Listener<RESPONSE> delegate, Context context) {
      super(delegate);
      this.context = context;
    }

    @Override
    public void onMessage(RESPONSE message) {
      Span span = Span.fromContext(context);
      Attributes attributes =
          Attributes.of(
              GrpcHelper.MESSAGE_TYPE, "SENT", GrpcHelper.MESSAGE_ID, messageId.incrementAndGet());
      span.addEvent("message", attributes);
      try (Scope ignored = context.makeCurrent()) {
        delegate().onMessage(message);
      } catch (Throwable e) {
        tracer.addThrowable(span, e);
        throw e;
      }
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      try (Scope ignored = context.makeCurrent()) {
        delegate().onClose(status, trailers);
      } catch (Throwable e) {
        tracer.endExceptionally(context, e);
        throw e;
      }
      tracer.end(context, status);
    }

    @Override
    public void onReady() {
      try (Scope ignored = context.makeCurrent()) {
        delegate().onReady();
      } catch (Throwable e) {
        tracer.endExceptionally(context, e);
        throw e;
      }
    }
  }
}
