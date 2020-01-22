package io.opentelemetry.auto.instrumentation.aws.v0;

import static io.opentelemetry.auto.instrumentation.aws.v0.RequestMeta.SPAN_SCOPE_PAIR_CONTEXT_KEY;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

/** Tracing Request Handler */
public class TracingRequestHandler extends RequestHandler2 {
  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  private final AwsSdkClientDecorator decorate;

  public TracingRequestHandler(
      final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore) {
    decorate = new AwsSdkClientDecorator(contextStore);
  }

  @Override
  public AmazonWebServiceRequest beforeMarshalling(final AmazonWebServiceRequest request) {
    return request;
  }

  @Override
  public void beforeRequest(final Request<?> request) {
    final Span span = TRACER.spanBuilder("aws.http").startSpan();
    decorate.afterStart(span);
    decorate.onRequest(span, request);
    request.addHandlerContext(
        SPAN_SCOPE_PAIR_CONTEXT_KEY, new SpanScopePair(span, TRACER.withSpan(span)));
  }

  @Override
  public void afterResponse(final Request<?> request, final Response<?> response) {
    final SpanScopePair spanScopePair = request.getHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY);
    if (spanScopePair != null) {
      request.addHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY, null);
      spanScopePair.getScope().close();
      final Span span = spanScopePair.getSpan();
      decorate.onResponse(span, response);
      decorate.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void afterError(final Request<?> request, final Response<?> response, final Exception e) {
    final SpanScopePair spanScopePair = request.getHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY);
    if (spanScopePair != null) {
      request.addHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY, null);
      spanScopePair.getScope().close();
      final Span span = spanScopePair.getSpan();
      decorate.onError(span, e);
      decorate.beforeFinish(span);
      span.end();
    }
  }
}
