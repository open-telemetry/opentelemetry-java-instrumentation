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
package io.opentelemetry.auto.instrumentation.grpc.client;

import static io.opentelemetry.auto.instrumentation.grpc.client.GrpcClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.grpc.client.GrpcClientDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.grpc.client.GrpcInjectAdapter.SETTER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.auto.instrumentation.grpc.common.GrpcHelper;
import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TracingClientInterceptor implements ClientInterceptor {
  private final InetSocketAddress peerAddress;

  public TracingClientInterceptor(final InetSocketAddress peerAddress) {
    this.peerAddress = peerAddress;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> method,
      final CallOptions callOptions,
      final Channel next) {

    final String methodName = method.getFullMethodName();
    final Span span = TRACER.spanBuilder(methodName).setSpanKind(CLIENT).startSpan();
    try (final Scope scope = currentContextWith(span)) {
      DECORATE.afterStart(span);
      GrpcHelper.prepareSpan(span, methodName, peerAddress, false);

      final ClientCall<ReqT, RespT> result;
      try {
        // call other interceptors
        result = next.newCall(method, callOptions);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.end();
        throw e;
      }
      return new TracingClientCall<>(span, result);
    }
  }

  static final class TracingClientCall<ReqT, RespT>
      extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {
    final Span span;

    TracingClientCall(final Span span, final ClientCall<ReqT, RespT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void start(final Listener<RespT> responseListener, final Metadata headers) {
      TRACER.getHttpTextFormat().inject(span.getContext(), headers, SETTER);
      try (final Scope scope = currentContextWith(span)) {
        super.start(new TracingClientCallListener<>(span, responseListener), headers);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.end();
        throw e;
      }
    }

    @Override
    public void sendMessage(final ReqT message) {
      try (final Scope scope = currentContextWith(span)) {
        super.sendMessage(message);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.end();
        throw e;
      }
    }
  }

  static final class TracingClientCallListener<RespT>
      extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {
    private final Span span;
    private final AtomicInteger messageId = new AtomicInteger();

    TracingClientCallListener(final Span span, final ClientCall.Listener<RespT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void onMessage(final RespT message) {
      final Map<String, AttributeValue> attributes = new HashMap<>();
      attributes.put("message.type", AttributeValue.stringAttributeValue("SENT"));
      attributes.put("message.id", AttributeValue.longAttributeValue(messageId.incrementAndGet()));
      span.addEvent("message", attributes);
      try (final Scope scope = currentContextWith(span)) {
        delegate().onMessage(message);
      }
    }

    @Override
    public void onClose(final Status status, final Metadata trailers) {
      DECORATE.onClose(span, status);
      // Finishes span.
      try (final Scope scope = currentContextWith(span)) {
        delegate().onClose(status, trailers);
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
