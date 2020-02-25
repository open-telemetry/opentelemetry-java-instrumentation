package io.opentelemetry.auto.instrumentation.grpc.server;

import static io.opentelemetry.auto.instrumentation.grpc.server.GrpcExtractAdapter.GETTER;
import static io.opentelemetry.auto.instrumentation.grpc.server.GrpcServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.grpc.server.GrpcServerDecorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.SERVER;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.AttributeValue;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
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

    final Span.Builder spanBuilder = TRACER.spanBuilder("grpc.server").setSpanKind(SERVER);
    try {
      final SpanContext extractedContext = TRACER.getHttpTextFormat().extract(headers, GETTER);
      spanBuilder.setParent(extractedContext);
    } catch (final IllegalArgumentException e) {
      // Couldn't extract a context. We should treat this as a root span.
      spanBuilder.setNoParent();
    }
    final Span span = spanBuilder.startSpan();
    span.setAttribute(MoreTags.RESOURCE_NAME, call.getMethodDescriptor().getFullMethodName());

    DECORATE.afterStart(span);

    final ServerCall.Listener<ReqT> result;
    try (final Scope scope = TRACER.withSpan(span)) {

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
      try (final Scope scope = TRACER.withSpan(span)) {
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
      try (final Scope scope = TRACER.withSpan(span)) {
        delegate().onMessage(message);
      }
    }

    @Override
    public void onHalfClose() {
      try (final Scope scope = TRACER.withSpan(span)) {
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
      try (final Scope scope = TRACER.withSpan(span)) {
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
      try (final Scope scope = TRACER.withSpan(span)) {
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
      try (final Scope scope = TRACER.withSpan(span)) {
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
