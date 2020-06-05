/*
 * Copyright The OpenTelemetry Authors
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

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.context.Scope;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static io.opentelemetry.auto.instrumentation.grpc.client.GrpcClientTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

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

    final GrpcClientSpan span = TRACER.startSpan(method, peerAddress);
    try (final Scope scope = currentContextWith(span)) {
      final ClientCall<ReqT, RespT> result;
      try {
        // call other interceptors
        result = next.newCall(method, callOptions);
      } catch (final Throwable e) {
        span.end(e);
        throw e;
      }
      return new TracingClientCall<>(span, result);
    }
  }

  static final class TracingClientCall<ReqT, RespT>
      extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {
    final GrpcClientSpan span;

    TracingClientCall(final GrpcClientSpan span, final ClientCall<ReqT, RespT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void start(final Listener<RespT> responseListener, final Metadata headers) {
      span.onRequest(headers);
      try (final Scope scope = currentContextWith(span)) {
        super.start(new TracingClientCallListener<>(span, responseListener), headers);
      } catch (final Throwable e) {
        span.end(e);
        throw e;
      }
    }

    @Override
    public void sendMessage(final ReqT message) {
      try (final Scope scope = currentContextWith(span)) {
        super.sendMessage(message);
      } catch (final Throwable e) {
        span.end(e);
        throw e;
      }
    }
  }

  static final class TracingClientCallListener<RespT>
      extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {
    private final GrpcClientSpan span;
    private final AtomicInteger messageId = new AtomicInteger();

    TracingClientCallListener(
        final GrpcClientSpan span, final ClientCall.Listener<RespT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void onMessage(final RespT message) {
      span.onMessage(messageId.incrementAndGet());
      try (final Scope scope = currentContextWith(span)) {
        delegate().onMessage(message);
      }
    }

    @Override
    public void onClose(final Status status, final Metadata trailers) {
      try (final Scope scope = currentContextWith(span)) {
        delegate().onClose(status, trailers);
      } finally {
        span.end(status);
      }
    }

    @Override
    public void onReady() {
      try (final Scope scope = currentContextWith(span)) {
        delegate().onReady();
      } catch (final Throwable e) {
        span.end(e);
        throw e;
      }
    }
  }
}
