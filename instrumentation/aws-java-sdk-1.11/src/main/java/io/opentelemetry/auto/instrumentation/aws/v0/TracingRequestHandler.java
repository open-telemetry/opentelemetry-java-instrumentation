package io.opentelemetry.auto.instrumentation.aws.v0;

import static io.opentelemetry.auto.instrumentation.aws.v0.RequestMeta.SPAN_SCOPE_PAIR_CONTEXT_KEY;
import static io.opentelemetry.trace.Span.Kind.CLIENT;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

/** Tracing Request Handler */
public class TracingRequestHandler extends RequestHandler2 {
  private static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

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
    final Span span = TRACER.spanBuilder("aws.http").setSpanKind(CLIENT).startSpan();
    decorate.afterStart(span);
    decorate.onRequest(span, request);
    request.addHandlerContext(
        SPAN_SCOPE_PAIR_CONTEXT_KEY, new SpanWithScope(span, TRACER.withSpan(span)));
  }

  @Override
  public void afterResponse(final Request<?> request, final Response<?> response) {
    final SpanWithScope spanWithScope = request.getHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY);
    if (spanWithScope != null) {
      request.addHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY, null);
      spanWithScope.closeScope();
      final Span span = spanWithScope.getSpan();
      decorate.onResponse(span, response);
      decorate.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void afterError(final Request<?> request, final Response<?> response, final Exception e) {
    final SpanWithScope spanWithScope = request.getHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY);
    if (spanWithScope != null) {
      request.addHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY, null);
      spanWithScope.closeScope();
      final Span span = spanWithScope.getSpan();
      decorate.onError(span, e);
      decorate.beforeFinish(span);
      span.end();
    }
  }
}
