package io.opentelemetry.helpers.core;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.distributedcontext.DistributedContextManager;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.trace.AttributeValue;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.util.Map;

/**
 * Abstract base span decorator implementation for HTTP server-side spans.
 *
 * @param <C> the context propagation carrier type
 * @param <Q> the request or input object type
 * @param <P> the response or output object type
 */
public abstract class HttpServerSpanDecorator<C, Q, P> extends ServerSpanDecorator<C, Q, P> {

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

  /**
   * Constructs a span decorator object.
   *
   * @param tracer the tracer to use in recording spans
   * @param contextManager the context manager to use in handling correlation contexts
   * @param meter the meter to use in recoding measurements
   * @param propagationGetter the decorator-specific context propagation getter
   */
  protected HttpServerSpanDecorator(
      Tracer tracer,
      DistributedContextManager contextManager,
      Meter meter,
      Getter propagationGetter) {
    super(tracer, contextManager, meter, propagationGetter);
  }

  @Override
  protected StatusTranslator<P> statusTranslator() {
    return httpStatusTranslator;
  }

  @Override
  protected void addSpanAttributes(Span span, C carrier, Q inbound) {
    putAttributeIfNotEmptyOrNull(span, SemanticConventions.HTTP_METHOD, getMethod(inbound));
    putAttributeIfNotEmptyOrNull(span, SemanticConventions.HTTP_ROUTE, getRoute(inbound));
    putAttributeIfNotEmptyOrNull(span, SemanticConventions.HTTP_URL, getUrl(inbound));
    putAttributeIfNotEmptyOrNull(span, SemanticConventions.HTTP_USER_AGENT, getUserAgent(inbound));
    putAttributeIfNotEmptyOrNull(span, SemanticConventions.HTTP_FLAVOR, getHttpFlavor(inbound));
    putAttributeIfNotEmptyOrNull(span, SemanticConventions.HTTP_CLIENT_IP, getClientIp(inbound));
  }

  @Override
  protected void addResultSpanAttributes(Span span, Throwable throwable, P outbound) {
    int httpStatus = getStatusCode(outbound);
    span.setAttribute(
        SemanticConventions.HTTP_STATUS_CODE, AttributeValue.longAttributeValue(httpStatus));
    if (throwable != null) {
      String message = extractErrorMessage(throwable);
      span.setAttribute(
          SemanticConventions.HTTP_STATUS_TEXT, AttributeValue.stringAttributeValue(message));
    }
  }

  /**
   * Returns the request method for use as the value of the <code>http.method</code> span attribute.
   *
   * @param request the HTTP request
   * @return the HTTP method
   */
  protected abstract String getMethod(Q request);

  /**
   * Returns the request URL for use as the value of the <code>http.url</code> span attribute.
   *
   * @param request the HTTP request
   * @return the request URL
   */
  protected abstract String getUrl(Q request);

  /**
   * Returns the request route for use as the span name. This should be in the format <code>
   * /users/:userID</code> or else the URI path.
   *
   * @param request the HTTP request
   * @return the request route
   */
  protected abstract String getRoute(Q request);

  /**
   * Returns the request user agent.
   *
   * @param request the HTTP request
   * @return the request user agent
   */
  protected abstract String getUserAgent(Q request);

  /**
   * Returns the HTTP protocol version used by the connection.
   *
   * @param request the HTTP request
   * @return the HTTP flavor
   */
  protected abstract String getHttpFlavor(Q request);

  /**
   * Returns the IP address of the calling client (Server-side only).
   *
   * @param request the HTTP request
   * @return the IP address
   */
  protected abstract String getClientIp(Q request);

  /**
   * Returns the response status code for use as the value of the <code>http.status_code</code> span
   * attribute. If the response is null, this method should return {@code 0}.
   *
   * @param response the HTTP response
   * @return the response status code
   */
  protected abstract int getStatusCode(P response);

  class HttpStatusTranslator implements StatusTranslator<P> {

    @Override
    public Status calculateStatus(Throwable throwable, P response) {
      int httpStatus = getStatusCode(response);
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
