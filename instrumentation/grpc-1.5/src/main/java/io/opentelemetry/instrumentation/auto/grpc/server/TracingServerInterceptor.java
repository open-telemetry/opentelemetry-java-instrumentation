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

package io.opentelemetry.instrumentation.auto.grpc.server;

import static io.opentelemetry.instrumentation.auto.grpc.server.GrpcServerTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.grpc.common.GrpcHelper;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;

public class TracingServerInterceptor implements ServerInterceptor {

  public static final TracingServerInterceptor INSTANCE = new TracingServerInterceptor();

  private TracingServerInterceptor() {}

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    String methodName = call.getMethodDescriptor().getFullMethodName();
    Span span = TRACER.startSpan(methodName, headers);

    SocketAddress addr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
    InetSocketAddress iAddr = addr instanceof InetSocketAddress ? (InetSocketAddress) addr : null;
    GrpcHelper.prepareSpan(span, methodName, iAddr, true);

    ServerCall.Listener<ReqT> result;
    try (Scope ignored = currentContextWith(span)) {

      try {
        // Wrap the server call so that we can decorate the span
        // with the resulting status
        TracingServerCall<ReqT, RespT> tracingServerCall = new TracingServerCall<>(span, call);

        // call other interceptors
        result = next.startCall(tracingServerCall, headers);
      } catch (Throwable e) {
        TRACER.endExceptionally(span, e);
        throw e;
      }
    }

    // This ensures the server implementation can see the span in scope
    return new TracingServerCallListener<>(span, result);
  }

  static final class TracingServerCall<ReqT, RespT>
      extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {
    final Span span;

    TracingServerCall(Span span, ServerCall<ReqT, RespT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void close(Status status, Metadata trailers) {
      TRACER.setStatus(span, status);
      try (Scope ignored = currentContextWith(span)) {
        delegate().close(status, trailers);
      } catch (Throwable e) {
        TRACER.endExceptionally(span, e);
        throw e;
      }
    }
  }

  static final class TracingServerCallListener<ReqT>
      extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
    private final Span span;
    private final AtomicLong messageId = new AtomicLong();

    TracingServerCallListener(Span span, ServerCall.Listener<ReqT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void onMessage(ReqT message) {
      Attributes attributes =
          Attributes.of(
              SemanticAttributes.GRPC_MESSAGE_TYPE,
              "RECEIVED",
              SemanticAttributes.GRPC_MESSAGE_ID,
              messageId.incrementAndGet());
      span.addEvent("message", attributes);
      try (Scope ignored = currentContextWith(span)) {
        delegate().onMessage(message);
      }
    }

    @Override
    public void onHalfClose() {
      try (Scope ignored = currentContextWith(span)) {
        delegate().onHalfClose();
      } catch (Throwable e) {
        TRACER.endExceptionally(span, e);
        throw e;
      }
    }

    @Override
    public void onCancel() {
      try (Scope ignored = currentContextWith(span)) {
        delegate().onCancel();
        span.setAttribute("canceled", true);
      } catch (Throwable e) {
        TRACER.endExceptionally(span, e);
        throw e;
      }
      TRACER.end(span);
    }

    @Override
    public void onComplete() {
      try (Scope ignored = currentContextWith(span)) {
        delegate().onComplete();
      } catch (Throwable e) {
        TRACER.endExceptionally(span, e);
        throw e;
      }
      TRACER.end(span);
    }

    @Override
    public void onReady() {
      try (Scope ignored = currentContextWith(span)) {
        delegate().onReady();
      } catch (Throwable e) {
        TRACER.endExceptionally(span, e);
        throw e;
      }
    }
  }
}
