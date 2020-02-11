package datadog.trace.instrumentation.grpc.server;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.grpc.server.GrpcExtractAdapter.GETTER;
import static datadog.trace.instrumentation.grpc.server.GrpcServerDecorator.DECORATE;

import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan.Context;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class TracingServerInterceptor implements ServerInterceptor {

  public static final TracingServerInterceptor INSTANCE = new TracingServerInterceptor();

  private TracingServerInterceptor() {}

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {

    final Context spanContext = propagate().extract(headers, GETTER);
    final AgentSpan span =
        startSpan("grpc.server", spanContext)
            .setTag(DDTags.RESOURCE_NAME, call.getMethodDescriptor().getFullMethodName());
    DECORATE.afterStart(span);

    final AgentScope scope = activateSpan(span, false);
    scope.setAsyncPropagation(true);

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
      span.finish();
      throw e;
    } finally {
      scope.setAsyncPropagation(false);
      scope.close();
    }

    // This ensures the server implementation can see the span in scope
    return new TracingServerCallListener<>(span, result);
  }

  static final class TracingServerCall<ReqT, RespT>
      extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {
    final AgentSpan span;

    TracingServerCall(final AgentSpan span, final ServerCall<ReqT, RespT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void close(final Status status, final Metadata trailers) {
      DECORATE.onClose(span, status);
      try (final AgentScope scope = activateSpan(span, false)) {
        scope.setAsyncPropagation(true);
        delegate().close(status, trailers);
        scope.setAsyncPropagation(false);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        throw e;
      }
    }
  }

  static final class TracingServerCallListener<ReqT>
      extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
    private final AgentSpan span;

    TracingServerCallListener(final AgentSpan span, final ServerCall.Listener<ReqT> delegate) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void onMessage(final ReqT message) {
      final AgentSpan span =
          startSpan("grpc.message", this.span.context())
              .setTag("message.type", message.getClass().getName());
      DECORATE.afterStart(span);
      final AgentScope scope = activateSpan(span, true);
      scope.setAsyncPropagation(true);
      try {
        delegate().onMessage(message);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(this.span);
        this.span.finish();
        throw e;
      } finally {
        scope.setAsyncPropagation(false);
        DECORATE.beforeFinish(span);
        scope.close();
      }
    }

    @Override
    public void onHalfClose() {
      try (final AgentScope scope = activateSpan(span, false)) {
        scope.setAsyncPropagation(true);
        delegate().onHalfClose();
        scope.setAsyncPropagation(false);
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
      try (final AgentScope scope = activateSpan(span, false)) {
        scope.setAsyncPropagation(true);
        delegate().onCancel();
        span.setTag("canceled", true);
        scope.setAsyncPropagation(false);
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
      try (final AgentScope scope = activateSpan(span, false)) {
        scope.setAsyncPropagation(true);
        delegate().onComplete();
        scope.setAsyncPropagation(false);
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
        scope.setAsyncPropagation(true);
        delegate().onReady();
        scope.setAsyncPropagation(false);
      } catch (final Throwable e) {
        DECORATE.onError(span, e);
        DECORATE.beforeFinish(span);
        span.finish();
        throw e;
      }
    }
  }
}
