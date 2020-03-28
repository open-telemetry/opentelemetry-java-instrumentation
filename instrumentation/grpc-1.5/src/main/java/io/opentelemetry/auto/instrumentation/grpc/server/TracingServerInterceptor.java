/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.grpc.server;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator.extract;
import static io.opentelemetry.auto.instrumentation.grpc.server.GrpcExtractAdapter.GETTER;
import static io.opentelemetry.auto.instrumentation.grpc.server.GrpcServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.grpc.server.GrpcServerDecorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.SERVER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.auto.instrumentation.grpc.common.GrpcHelper;
import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TracingServerInterceptor implements ServerInterceptor {

  public static final TracingServerInterceptor INSTANCE = new TracingServerInterceptor();

  private TracingServerInterceptor() {}

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {

    final String methodName = call.getMethodDescriptor().getFullMethodName();
    final Span.Builder spanBuilder = TRACER.spanBuilder(methodName).setSpanKind(SERVER);
    final SpanContext extractedContext = extract(headers, GETTER);
    if (extractedContext.isValid()) {
      spanBuilder.setParent(extractedContext);
    } else {
      // explicitly setting "no parent" in case a span was propagated to this thread
      // by the java-concurrent instrumentation when the thread was started
      spanBuilder.setNoParent();
    }
    final Span span = spanBuilder.startSpan();
    final SocketAddress addr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
    final InetSocketAddress iAddr =
        addr instanceof InetSocketAddress ? (InetSocketAddress) addr : null;
    GrpcHelper.prepareSpan(span, methodName, iAddr, true);

    DECORATE.afterStart(span);

    final ServerCall.Listener<ReqT> result;
    try (final Scope scope = currentContextWith(span)) {

      try {
        // Wrap the server call so that we can decorate the span
        // with the resulting status
        final TracingServerCall<ReqT, RespT> tracingServerCall =
            new TracingServerCall<>(span, call);

        // call other interceptors
        result = next.startCall(tracingServerCall, headers);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.end();
        throw e;
      }
    }

    // This ensures the server implementation can see the span in scope
    return new TracingServerCallListener<>(span, result);
  }

  static final class TracingServerCall<ReqT, RespT>
      extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {
    final Span span;

    TracingServerCall(final Span span, final ServerCall<ReqT, RespT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void close(final Status status, final Metadata trailers) {
      DECORATE.onClose(span, status);
      try (final Scope scope = currentContextWith(span)) {
        delegate().close(status, trailers);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        throw e;
      }
    }
  }

  static final class TracingServerCallListener<ReqT>
      extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
    private final Span span;
    private final AtomicInteger messageId = new AtomicInteger();

    TracingServerCallListener(final Span span, final ServerCall.Listener<ReqT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void onMessage(final ReqT message) {
      final Map<String, AttributeValue> attributes = new HashMap<>();
      attributes.put("message.type", AttributeValue.stringAttributeValue("RECEIVED"));
      attributes.put("message.id", AttributeValue.longAttributeValue(messageId.incrementAndGet()));
      span.addEvent("message", attributes);
      try (final Scope scope = currentContextWith(span)) {
        delegate().onMessage(message);
      }
    }

    @Override
    public void onHalfClose() {
      try (final Scope scope = currentContextWith(span)) {
        delegate().onHalfClose();
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.end();
        throw e;
      }
    }

    @Override
    public void onCancel() {
      // Finishes span.
      try (final Scope scope = currentContextWith(span)) {
        delegate().onCancel();
        span.setAttribute("canceled", true);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        throw e;
      } finally {
        DECORATE.beforeFinish(span);
        span.end();
      }
    }

    @Override
    public void onComplete() {
      // Finishes span.
      try (final Scope scope = currentContextWith(span)) {
        delegate().onComplete();
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        throw e;
      } finally {
        DECORATE.beforeFinish(span);
        span.end();
      }
    }

    @Override
    public void onReady() {
      try (final Scope scope = currentContextWith(span)) {
        delegate().onReady();
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.end();
        throw e;
      }
    }
  }
}
