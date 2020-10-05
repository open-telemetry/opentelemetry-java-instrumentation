/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.grpc.client;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.instrumentation.auto.grpc.client.GrpcClientTracer.TRACER;
import static io.opentelemetry.instrumentation.auto.grpc.client.GrpcInjectAdapter.SETTER;
import static io.opentelemetry.trace.TracingContextUtils.getSpan;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.grpc.common.GrpcHelper;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

public class TracingClientInterceptor implements ClientInterceptor {
  private final InetSocketAddress peerAddress;

  public TracingClientInterceptor(InetSocketAddress peerAddress) {
    this.peerAddress = peerAddress;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

    String methodName = method.getFullMethodName();
    Span span = TRACER.startSpan(methodName);
    Context context = withSpan(span, Context.current());
    try (Scope ignored = withScopedContext(context)) {
      GrpcHelper.prepareSpan(span, methodName, peerAddress, false);

      ClientCall<ReqT, RespT> result;
      try {
        // call other interceptors
        result = next.newCall(method, callOptions);
      } catch (Throwable e) {
        TRACER.endExceptionally(span, e);
        throw e;
      }
      return new TracingClientCall<>(context, result);
    }
  }

  static final class TracingClientCall<ReqT, RespT>
      extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {
    final Context context;

    TracingClientCall(Context context, ClientCall<ReqT, RespT> delegate) {
      super(delegate);
      this.context = context;
    }

    @Override
    public void start(Listener<RespT> responseListener, Metadata headers) {
      // this reference to io.grpc.Context will be shaded during the build
      // see instrumentation.gradle: "relocate OpenTelemetry API dependency usage"
      // (luckily the grpc instrumentation doesn't need to reference unshaded grpc Context, so we
      // don't need to worry about distinguishing them like in the opentelemetry-api
      // instrumentation)
      OpenTelemetry.getPropagators().getTextMapPropagator().inject(context, headers, SETTER);
      try (Scope ignored = withScopedContext(context)) {
        super.start(new TracingClientCallListener<>(context, responseListener), headers);
      } catch (Throwable e) {
        Span span = getSpan(context);
        TRACER.endExceptionally(span, e);
        throw e;
      }
    }

    @Override
    public void sendMessage(ReqT message) {
      try (Scope ignored = withScopedContext(context)) {
        super.sendMessage(message);
      } catch (Throwable e) {
        Span span = getSpan(context);
        TRACER.endExceptionally(span, e);
        throw e;
      }
    }
  }

  static final class TracingClientCallListener<RespT>
      extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {
    private final Context context;
    private final AtomicLong messageId = new AtomicLong();

    TracingClientCallListener(Context context, ClientCall.Listener<RespT> delegate) {
      super(delegate);
      this.context = context;
    }

    @Override
    public void onMessage(RespT message) {
      Span span = getSpan(context);
      Attributes attributes =
          Attributes.of(
              SemanticAttributes.GRPC_MESSAGE_TYPE,
              "SENT",
              SemanticAttributes.GRPC_MESSAGE_ID,
              messageId.incrementAndGet());
      span.addEvent("message", attributes);
      try (Scope ignored = withScopedContext(context)) {
        delegate().onMessage(message);
      }
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      Span span = getSpan(context);
      try (Scope ignored = withScopedContext(context)) {
        delegate().onClose(status, trailers);
      } catch (Throwable e) {
        TRACER.endExceptionally(span, e);
        throw e;
      }
      TRACER.endSpan(span, status);
    }

    @Override
    public void onReady() {
      try (Scope ignored = withScopedContext(context)) {
        delegate().onReady();
      } catch (Throwable e) {
        Span span = getSpan(context);
        TRACER.endExceptionally(span, e);
        throw e;
      }
    }
  }
}
