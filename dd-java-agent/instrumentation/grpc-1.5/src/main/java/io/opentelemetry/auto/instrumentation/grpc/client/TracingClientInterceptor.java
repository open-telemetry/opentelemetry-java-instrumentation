package io.opentelemetry.auto.instrumentation.grpc.client;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.propagate;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.instrumentation.grpc.client.GrpcClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.grpc.client.GrpcInjectAdapter.SETTER;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;

public class TracingClientInterceptor implements ClientInterceptor {

  public static final TracingClientInterceptor INSTANCE = new TracingClientInterceptor();

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> method,
      final CallOptions callOptions,
      final Channel next) {

    final AgentSpan span =
        startSpan("grpc.client").setTag(MoreTags.RESOURCE_NAME, method.getFullMethodName());
    try (final AgentScope scope = activateSpan(span, false)) {
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

      return new TracingClientCall<>(span, result);
    }
  }

  static final class TracingClientCall<ReqT, RespT>
      extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {
    final AgentSpan span;

    TracingClientCall(final AgentSpan span, final ClientCall<ReqT, RespT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void start(final Listener<RespT> responseListener, final Metadata headers) {
      propagate().inject(span, headers, SETTER);

      try (final AgentScope scope = activateSpan(span, false)) {
        super.start(new TracingClientCallListener<>(span, responseListener), headers);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.finish();
        throw e;
      }
    }

    @Override
    public void sendMessage(final ReqT message) {
      try (final AgentScope scope = activateSpan(span, false)) {
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
    final AgentSpan span;

    TracingClientCallListener(final AgentSpan span, final ClientCall.Listener<RespT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void onMessage(final RespT message) {
      final AgentSpan messageSpan =
          startSpan("grpc.message", span.context())
              .setTag("message.type", message.getClass().getName());
      DECORATE.afterStart(messageSpan);
      final AgentScope scope = activateSpan(messageSpan, true);
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
      try (final AgentScope scope = activateSpan(span, false)) {
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
      try (final AgentScope scope = activateSpan(span, false)) {
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
