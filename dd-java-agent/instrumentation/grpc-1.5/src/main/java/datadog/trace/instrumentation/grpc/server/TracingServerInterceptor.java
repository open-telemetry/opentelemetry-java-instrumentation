package datadog.trace.instrumentation.grpc.server;

import static datadog.trace.instrumentation.grpc.server.GrpcServerDecorator.DECORATE;

import datadog.trace.api.DDTags;
import datadog.trace.context.TraceScope;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import java.util.HashMap;
import java.util.Map;

public class TracingServerInterceptor implements ServerInterceptor {

  private final Tracer tracer;

  public TracingServerInterceptor(final Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {

    final Map<String, String> headerMap = new HashMap<>();
    for (final String key : headers.keys()) {
      if (!key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
        final String value = headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
        headerMap.put(key, value);
      }
    }
    final SpanContext spanContext =
        tracer.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(headerMap));

    final Tracer.SpanBuilder spanBuilder =
        tracer
            .buildSpan("grpc.server")
            .withTag(DDTags.RESOURCE_NAME, call.getMethodDescriptor().getFullMethodName());
    if (spanContext != null) {
      spanBuilder.asChildOf(spanContext);
    }
    final Scope scope = spanBuilder.startActive(false);

    if (scope instanceof TraceScope) {
      ((TraceScope) scope).setAsyncPropagation(true);
    }

    final Span span = scope.span();
    DECORATE.afterStart(span);

    final ServerCall.Listener<ReqT> result;
    try {
      // call other interceptors
      result = next.startCall(call, headers);
    } catch (final Throwable e) {
      DECORATE.onError(span, e);
      DECORATE.beforeFinish(span);
      span.finish();
      throw e;
    } finally {
      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(false);
      }
      scope.close();
    }

    // This ensures the server implementation can see the span in scope
    return new TracingServerCallListener<>(tracer, span, result);
  }

  static final class TracingServerCallListener<ReqT>
      extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
    final Tracer tracer;
    final Span span;

    TracingServerCallListener(
        final Tracer tracer, final Span span, final ServerCall.Listener<ReqT> delegate) {
      super(delegate);
      this.tracer = tracer;
      this.span = span;
    }

    @Override
    public void onMessage(final ReqT message) {
      final Scope scope =
          tracer
              .buildSpan("grpc.message")
              .asChildOf(span)
              .withTag("message.type", message.getClass().getName())
              .startActive(true);
      DECORATE.afterStart(scope.span());
      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }
      try {
        delegate().onMessage(message);
      } catch (final Throwable e) {
        final Span span = scope.span();
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        this.span.finish();
        throw e;
      } finally {
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(false);
        }
        DECORATE.afterStart(scope.span());
        scope.close();
      }
    }

    @Override
    public void onHalfClose() {
      try (final Scope scope = tracer.scopeManager().activate(span, false)) {
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(true);
        }
        delegate().onHalfClose();
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(false);
        }
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.finish();
        throw e;
      }
    }

    @Override
    public void onCancel() {
      // Finishes span.
      try (final Scope scope = tracer.scopeManager().activate(span, false)) {
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(true);
        }
        delegate().onCancel();
        span.setTag("canceled", true);
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(false);
        }
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        throw e;
      } finally {
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }

    @Override
    public void onComplete() {
      // Finishes span.
      try (final Scope scope = tracer.scopeManager().activate(span, false)) {
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(true);
        }
        delegate().onComplete();
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(false);
        }
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        throw e;
      } finally {
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }

    @Override
    public void onReady() {
      try (final Scope scope = tracer.scopeManager().activate(span, false)) {
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(true);
        }
        delegate().onReady();
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(false);
        }
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.finish();
        throw e;
      }
    }
  }
}
