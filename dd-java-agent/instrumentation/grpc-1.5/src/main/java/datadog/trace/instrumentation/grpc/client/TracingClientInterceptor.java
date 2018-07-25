package datadog.trace.instrumentation.grpc.client;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.DDSpanTypes;
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
import io.opentracing.tag.Tags;
import java.util.Collections;

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

    final Scope scope =
        tracer
            .buildSpan("grpc.client")
            .withTag(DDTags.RESOURCE_NAME, method.getFullMethodName())
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.RPC)
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .startActive(false);
    final Span span = scope.span();

    final ClientCall<ReqT, RespT> result;
    try {
      // call other interceptors
      result = next.newCall(method, callOptions);
    } catch (final RuntimeException | Error e) {
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap(ERROR_OBJECT, e));
      span.finish();
      throw e;
    } finally {
      scope.close();
    }

    return new TracingClientCall<>(tracer, span, result);
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
      } catch (final RuntimeException | Error e) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, e));
        span.finish();
        throw e;
      }
    }

    @Override
    public void sendMessage(final ReqT message) {
      try (final Scope ignored = tracer.scopeManager().activate(span, false)) {
        super.sendMessage(message);
      } catch (final RuntimeException | Error e) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, e));
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
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.RPC)
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
              .startActive(true);
      try {
        delegate().onMessage(message);
      } catch (final RuntimeException | Error e) {
        final Span span = scope.span();
        Tags.ERROR.set(span, true);
        this.span.log(Collections.singletonMap(ERROR_OBJECT, e));
        this.span.finish();
        throw e;
      } finally {
        scope.close();
      }
    }

    @Override
    public void onClose(final Status status, final Metadata trailers) {
      span.setTag("status.code", status.getCode().name());
      if (status.getDescription() != null) {
        span.setTag("status.description", status.getDescription());
      }
      if (!status.isOk()) {
        Tags.ERROR.set(span, true);
      }
      if (status.getCause() != null) {
        span.log(Collections.singletonMap(ERROR_OBJECT, status.getCause()));
      }
      // Finishes span.
      try (final Scope ignored = tracer.scopeManager().activate(span, true)) {
        delegate().onClose(status, trailers);
      } catch (final RuntimeException | Error e) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, e));
        span.finish();
        throw e;
      }
    }

    @Override
    public void onReady() {
      try (final Scope ignored = tracer.scopeManager().activate(span, false)) {
        delegate().onReady();
      } catch (final RuntimeException | Error e) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, e));
        span.finish();
        throw e;
      }
    }
  }
}
