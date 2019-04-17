package datadog.trace.instrumentation.grpc.client;

import static datadog.trace.instrumentation.grpc.client.GrpcClientDecorator.DECORATE;

import datadog.trace.api.DDTags;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;

public class TracingClientInterceptor implements ClientInterceptor {

  private final Tracer tracer;

  public TracingClientInterceptor(final Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> method,
      final CallOptions callOptions,
      final Channel next) {

    final Span span =
        tracer
            .buildSpan("grpc.client")
            .withTag(DDTags.RESOURCE_NAME, method.getFullMethodName())
            .start();
    try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
      DECORATE.afterStart(span);

      final ClientCall<ReqT, RespT> result;
      try {
        // call other interceptors
        result = next.newCall(method, callOptions);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.finish();
        throw e;
      }

      return new TracingClientCall<>(tracer, span, result);
    }
  }

  static final class TracingClientCall<ReqT, RespT>
      extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {
    final Tracer tracer;
    final Span span;

    TracingClientCall(
        final Tracer tracer, final Span span, final ClientCall<ReqT, RespT> delegate) {
      super(delegate);
      this.tracer = tracer;
      this.span = span;
    }

    @Override
    public void start(final Listener<RespT> responseListener, final Metadata headers) {
      tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new GrpcInjectAdapter(headers));

      try (final Scope ignored = tracer.scopeManager().activate(span, false)) {
        super.start(new TracingClientCallListener<>(tracer, span, responseListener), headers);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.finish();
        throw e;
      }
    }

    @Override
    public void sendMessage(final ReqT message) {
      try (final Scope ignored = tracer.scopeManager().activate(span, false)) {
        super.sendMessage(message);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.finish();
        throw e;
      }
    }
  }

  static final class TracingClientCallListener<RespT>
      extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {
    final Tracer tracer;
    final Span span;

    TracingClientCallListener(
        final Tracer tracer, final Span span, final ClientCall.Listener<RespT> delegate) {
      super(delegate);
      this.tracer = tracer;
      this.span = span;
    }

    @Override
    public void onMessage(final RespT message) {
      final Scope scope =
          tracer
              .buildSpan("grpc.message")
              .asChildOf(span)
              .withTag("message.type", message.getClass().getName())
              .startActive(true);
      final Span messageSpan = scope.span();
      DECORATE.afterStart(messageSpan);
      try {
        delegate().onMessage(message);
      } catch (final Throwable e) {
        DECORATE.onError(messageSpan, e);
        throw e;
      } finally {
        DECORATE.beforeFinish(messageSpan);
        scope.close();
      }
    }

    @Override
    public void onClose(final Status status, final Metadata trailers) {
      DECORATE.onClose(span, status);
      // Finishes span.
      try (final Scope ignored = tracer.scopeManager().activate(span, false)) {
        delegate().onClose(status, trailers);
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
      try (final Scope ignored = tracer.scopeManager().activate(span, false)) {
        delegate().onReady();
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.finish();
        throw e;
      }
    }
  }
}
