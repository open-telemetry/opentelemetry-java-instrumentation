package io.opentelemetry.auto.instrumentation.grpc.server;

import static io.opentelemetry.auto.instrumentation.grpc.server.GrpcExtractAdapter.GETTER;
import static io.opentelemetry.auto.instrumentation.grpc.server.GrpcServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.grpc.server.GrpcServerDecorator.TRACER;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;

public class TracingServerInterceptor implements ServerInterceptor {

  public static final TracingServerInterceptor INSTANCE = new TracingServerInterceptor();

  private TracingServerInterceptor() {}

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {

    final SpanContext spanContext = TRACER.getHttpTextFormat().extract(headers, GETTER);
    final Span span = TRACER.spanBuilder("grpc.server").setParent(spanContext).startSpan();
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

    TracingServerCallListener(final Span span, final ServerCall.Listener<ReqT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void onMessage(final ReqT message) {
      final Span span =
          TRACER.spanBuilder("grpc.message").setParent(this.span.getContext()).startSpan();
      span.setAttribute("message.type", message.getClass().getName());
      DECORATE.afterStart(span);
      final Scope scope = TRACER.withSpan(span);
      try {
        delegate().onMessage(message);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(this.span);
        this.span.end();
        throw e;
      } finally {
        DECORATE.beforeFinish(span);
        span.end();
        scope.close();
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
