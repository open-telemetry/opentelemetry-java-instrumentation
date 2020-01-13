package io.opentelemetry.auto.instrumentation.aws.v0;

import static io.opentelemetry.auto.instrumentation.aws.v0.AwsSdkClientDecorator.DECORATE;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

/** Tracing Request Handler */
public class TracingRequestHandler extends RequestHandler2 {
  public static TracingRequestHandler INSTANCE = new TracingRequestHandler();

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  // Note: aws1.x sdk doesn't have any truly async clients so we can store scope in request context
  // safely.
  public static final HandlerContextKey<SpanScopePair> SPAN_SCOPE_PAIR_CONTEXT_KEY =
      new HandlerContextKey<>("io.opentelemetry.auto.SpanScopePair");

  @Override
  public AmazonWebServiceRequest beforeMarshalling(final AmazonWebServiceRequest request) {
    return request;
  }

  @Override
  public void beforeRequest(final Request<?> request) {
    final Span span = TRACER.spanBuilder("aws.http").startSpan();
    DECORATE.afterStart(span);
    DECORATE.onRequest(span, request);
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
      DECORATE.onResponse(span, response);
      DECORATE.beforeFinish(span);
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
      DECORATE.onError(span, e);
      DECORATE.beforeFinish(span);
      span.end();
    }
  }
}
