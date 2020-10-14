/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5.client;

import static io.opentelemetry.instrumentation.grpc.v1_5.client.GrpcInjectAdapter.SETTER;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

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
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.instrumentation.grpc.v1_5.common.GrpcHelper;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.attributes.SemanticAttributes;
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
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    String methodName = method.getFullMethodName();
    Span span = tracer.startSpan(methodName);
    GrpcHelper.prepareSpan(span, methodName);
    Context context = withSpan(span, Context.current());
    final ClientCall<ReqT, RespT> result;
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
      NetPeerUtils.setNetPeer(span, inetSocketAddress);
    }

    return new TracingClientCall<>(result, span, context, tracer);
  }

  static final class TracingClientCall<ReqT, RespT>
      extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {

    private final Span span;
    private final Context context;
    private final GrpcClientTracer tracer;

    TracingClientCall(
        ClientCall<ReqT, RespT> delegate, Span span, Context context, GrpcClientTracer tracer) {
      super(delegate);
      this.span = span;
      this.context = context;
      this.tracer = tracer;
    }

    @Override
    public void start(Listener<RespT> responseListener, Metadata headers) {
      OpenTelemetry.getPropagators().getTextMapPropagator().inject(context, headers, SETTER);
      try {
        super.start(new TracingClientCallListener<>(responseListener, span, tracer), headers);
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
    }

    @Override
    public void sendMessage(ReqT message) {
      try {
        super.sendMessage(message);
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
    }
  }

  static final class TracingClientCallListener<RespT>
      extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {
    private final Span span;
    private final GrpcClientTracer tracer;

    private final AtomicLong messageId = new AtomicLong();

    TracingClientCallListener(Listener<RespT> delegate, Span span, GrpcClientTracer tracer) {
      super(delegate);
      this.span = span;
      this.tracer = tracer;
    }

    @Override
    public void onMessage(RespT message) {
      Attributes attributes =
          Attributes.of(
              SemanticAttributes.GRPC_MESSAGE_TYPE,
              "SENT",
              SemanticAttributes.GRPC_MESSAGE_ID,
              messageId.incrementAndGet());
      span.addEvent("message", attributes);
      try {
        delegate().onMessage(message);
      } catch (Throwable e) {
        tracer.addThrowable(span, e);
      }
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      try {
        delegate().onClose(status, trailers);
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
      tracer.endSpan(span, status);
    }

    @Override
    public void onReady() {
      try {
        delegate().onReady();
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
    }
  }
}
