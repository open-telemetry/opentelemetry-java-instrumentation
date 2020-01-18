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
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
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

    final SpanScopePair spanAndScope = new SpanScopePair(span, TRACER.withSpan(span));

    final ServerCall.Listener<ReqT> result;
    try {
      // Wrap the server call so that we can decorate the span
      // with the resulting status
      final TracingServerCall<ReqT, RespT> tracingServerCall = new TracingServerCall<>(span, call);

      // call other interceptors
      result = next.startCall(tracingServerCall, headers);
    } catch (final Throwable e) {
      DECORATE.onError(span, e);
      DECORATE.beforeFinish(span);
      span.end();
      throw e;
    } finally {
      spanAndScope.getScope().close();
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
      final SpanScopePair spanAndScope = new SpanScopePair(span, TRACER.withSpan(span));
      try {
        delegate().close(status, trailers);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        throw e;
      } finally {
        spanAndScope.getScope().close();
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
      final SpanScopePair spanAndScope = new SpanScopePair(span, TRACER.withSpan(span));
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
        spanAndScope.getScope().close();
      }
    }

    @Override
    public void onHalfClose() {
      final SpanScopePair spanAndScope = new SpanScopePair(span, TRACER.withSpan(span));
      try {
        delegate().onHalfClose();
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.end();
        throw e;
      } finally {
        spanAndScope.getScope().close();
      }
    }

    @Override
    public void onCancel() {
      // Finishes span.
      final SpanScopePair spanAndScope = new SpanScopePair(span, TRACER.withSpan(span));
      try {
        delegate().onCancel();
        span.setAttribute("canceled", true);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        throw e;
      } finally {
        DECORATE.beforeFinish(span);
        span.end();
        spanAndScope.getScope().close();
      }
    }

    @Override
    public void onComplete() {
      // Finishes span.
      final SpanScopePair spanAndScope = new SpanScopePair(span, TRACER.withSpan(span));
      try {
        delegate().onComplete();
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        throw e;
      } finally {
        DECORATE.beforeFinish(span);
        span.end();
        spanAndScope.getScope().close();
      }
    }

    @Override
    public void onReady() {
      final SpanScopePair spanAndScope = new SpanScopePair(span, TRACER.withSpan(span));
      try {
        delegate().onReady();
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.end();
        spanAndScope.getScope().close();
        throw e;
      } finally {
        spanAndScope.getScope().close();
      }
    }
  }
}
