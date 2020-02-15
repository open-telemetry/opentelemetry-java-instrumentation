package io.opentelemetry.helpers.core;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.distributedcontext.DistributedContextManager;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.trace.AttributeValue;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.util.Map;

/**
 * Abstract base span decorator implementation for HTTP client-side spans.
 *
 * @param <C> the context propagation carrier type
 * @param <Q> the request or input object type
 * @param <P> the response or output object type
 */
public abstract class HttpClientSpanDecorator<C, Q, P> extends ClientSpanDecorator<C, Q, P> {

  private static final Map<Integer, Status> STATUS_MAP;

  static {
    ImmutableMap.Builder<Integer, Status> builder = ImmutableMap.builder();
    builder.put(400, Status.INVALID_ARGUMENT);
    builder.put(401, Status.UNAUTHENTICATED);
    builder.put(403, Status.PERMISSION_DENIED);
    builder.put(404, Status.NOT_FOUND);
    builder.put(429, Status.RESOURCE_EXHAUSTED);
    builder.put(500, Status.INTERNAL);
    builder.put(501, Status.UNIMPLEMENTED);
    builder.put(503, Status.UNAVAILABLE);
    builder.put(504, Status.DEADLINE_EXCEEDED);
    STATUS_MAP = builder.build();
  }

  private HttpStatusTranslator httpStatusTranslator = new HttpStatusTranslator();
  private final HttpExtractor<Q, P> httpExtractor;

  /**
   * Constructs a span decorator object.
   *
   * @param tracer the tracer to use in recording spans
   * @param contextManager the context manager to use in handling correlation contexts
   * @param meter the meter to use in recoding measurements
   * @param propagationSetter the decorator-specific context propagation setter
   * @param httpExtractor the extractor used to extract information from request/response
   */
  protected HttpClientSpanDecorator(
      Tracer tracer,
      DistributedContextManager contextManager,
      Meter meter,
      Setter<C> propagationSetter,
      HttpExtractor<Q, P> httpExtractor) {
    super(tracer, contextManager, meter, propagationSetter);
    assert httpExtractor != null;
    this.httpExtractor = httpExtractor;
  }

  @Override
  protected StatusTranslator<P> statusTranslator() {
    return httpStatusTranslator;
  }

  protected HttpExtractor<Q, P> extractor() {
    return httpExtractor;
  }

  @Override
  protected void addSpanAttributes(Span span, C carrier, Q inbound) {
    putAttributeIfNotEmptyOrNull(
        span, SemanticConventions.HTTP_METHOD, extractor().getMethod(inbound));
    putAttributeIfNotEmptyOrNull(
        span, SemanticConventions.HTTP_ROUTE, extractor().getRoute(inbound));
    putAttributeIfNotEmptyOrNull(span, SemanticConventions.HTTP_URL, extractor().getUrl(inbound));
    putAttributeIfNotEmptyOrNull(
        span, SemanticConventions.HTTP_USER_AGENT, extractor().getUserAgent(inbound));
    putAttributeIfNotEmptyOrNull(
        span, SemanticConventions.HTTP_FLAVOR, extractor().getHttpFlavor(inbound));
    putAttributeIfNotEmptyOrNull(
        span, SemanticConventions.HTTP_CLIENT_IP, extractor().getClientIp(inbound));
  }

  @Override
  protected void addResultSpanAttributes(Span span, Throwable throwable, P outbound) {
    int httpStatus = extractor().getStatusCode(outbound);
    span.setAttribute(
        SemanticConventions.HTTP_STATUS_CODE, AttributeValue.longAttributeValue(httpStatus));
    if (throwable != null) {
      String message = extractErrorMessage(throwable);
      span.setAttribute(
          SemanticConventions.HTTP_STATUS_TEXT, AttributeValue.stringAttributeValue(message));
    }
  }

  class HttpStatusTranslator implements StatusTranslator<P> {

    @Override
    public Status calculateStatus(Throwable throwable, P response) {
      int httpStatus = extractor().getStatusCode(response);
      Status status = null;
      if (httpStatus >= 200 && httpStatus < 400) {
        return Status.OK;
      }
      status = STATUS_MAP.get(httpStatus);
      if (status != null) {
        return status;
      }
      if (httpStatus >= 400 && httpStatus < 500) {
        status = Status.INVALID_ARGUMENT;
      } else if (httpStatus >= 500 && httpStatus < 600) {
        status = Status.INTERNAL;
      } else {
        status = Status.UNKNOWN;
      }
      return status;
    }
  }
}
