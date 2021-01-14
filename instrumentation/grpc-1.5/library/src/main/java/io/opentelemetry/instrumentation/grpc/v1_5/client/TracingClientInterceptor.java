/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5.client;

import static io.opentelemetry.instrumentation.grpc.v1_5.client.GrpcInjectAdapter.SETTER;

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
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.instrumentation.grpc.v1_5.common.GrpcHelper;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;

public class TracingClientInterceptor implements ClientInterceptor {

  public static ClientInterceptor newInterceptor() {
    return newInterceptor(new GrpcClientTracer());
  }

  public static ClientInterceptor newInterceptor(Tracer tracer) {
    return newInterceptor(new GrpcClientTracer(tracer));
  }

  public static ClientInterceptor newInterceptor(GrpcClientTracer tracer) {
    return new TracingClientInterceptor(tracer);
  }

  private final GrpcClientTracer tracer;

  private TracingClientInterceptor(GrpcClientTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public <REQUEST, RESPONSE> ClientCall<REQUEST, RESPONSE> interceptCall(
      MethodDescriptor<REQUEST, RESPONSE> method, CallOptions callOptions, Channel next) {
    String methodName = method.getFullMethodName();
    Span span = tracer.startSpan(methodName);
    GrpcHelper.prepareSpan(span, methodName);
    Context context = Context.current().with(span);
    final ClientCall<REQUEST, RESPONSE> result;
    try (Scope ignored = context.makeCurrent()) {
      try {
        // call other interceptors
        result = next.newCall(method, callOptions);
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
    }

    SocketAddress address = result.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
    if (address instanceof InetSocketAddress) {
      InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
      NetPeerUtils.INSTANCE.setNetPeer(span, inetSocketAddress);
    }

    return new TracingClientCall<>(result, span, context, tracer);
  }

  static final class TracingClientCall<REQUEST, RESPONSE>
      extends ForwardingClientCall.SimpleForwardingClientCall<REQUEST, RESPONSE> {

    private final Span span;
    private final Context context;
    private final GrpcClientTracer tracer;

    TracingClientCall(
        ClientCall<REQUEST, RESPONSE> delegate,
        Span span,
        Context context,
        GrpcClientTracer tracer) {
      super(delegate);
      this.span = span;
      this.context = context;
      this.tracer = tracer;
    }

    @Override
    public void start(Listener<RESPONSE> responseListener, Metadata headers) {
      GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(context, headers, SETTER);
      try (Scope ignored = span.makeCurrent()) {
        super.start(new TracingClientCallListener<>(responseListener, span, tracer), headers);
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
    }

    @Override
    public void sendMessage(REQUEST message) {
      try (Scope ignored = span.makeCurrent()) {
        super.sendMessage(message);
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
    }
  }

  static final class TracingClientCallListener<RESPONSE>
      extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RESPONSE> {
    private final Span span;
    private final GrpcClientTracer tracer;

    private final AtomicLong messageId = new AtomicLong();

    TracingClientCallListener(Listener<RESPONSE> delegate, Span span, GrpcClientTracer tracer) {
      super(delegate);
      this.span = span;
      this.tracer = tracer;
    }

    @Override
    public void onMessage(RESPONSE message) {
      Attributes attributes =
          Attributes.of(
              GrpcHelper.MESSAGE_TYPE, "SENT", GrpcHelper.MESSAGE_ID, messageId.incrementAndGet());
      span.addEvent("message", attributes);
      try (Scope ignored = span.makeCurrent()) {
        delegate().onMessage(message);
      } catch (Throwable e) {
        tracer.addThrowable(span, e);
      }
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      try (Scope ignored = span.makeCurrent()) {
        delegate().onClose(status, trailers);
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
      tracer.endSpan(span, status);
    }

    @Override
    public void onReady() {
      try (Scope ignored = span.makeCurrent()) {
        delegate().onReady();
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
    }
  }
}
