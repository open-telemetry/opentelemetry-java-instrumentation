package datadog.trace.agent.decorator;

import datadog.trace.api.Config;
import datadog.trace.api.DDSpanTypes;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

public abstract class HttpServerDecorator<REQUEST, RESPONSE> extends ServerDecorator {

  protected abstract String method(REQUEST request);

  protected abstract String url(REQUEST request);

  protected abstract String hostname(REQUEST request);

  protected abstract Integer port(REQUEST request);

  protected abstract Integer status(RESPONSE response);

  @Override
  protected String spanType() {
    return DDSpanTypes.HTTP_SERVER;
  }

  @Override
  protected boolean traceAnalyticsDefault() {
    return Config.getBooleanSettingFromEnvironment(Config.TRACE_ANALYTICS_ENABLED, false);
  }

  public Span onRequest(final Span span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      Tags.HTTP_METHOD.set(span, method(request));
      Tags.HTTP_URL.set(span, url(request));
      Tags.PEER_HOSTNAME.set(span, hostname(request));
      Tags.PEER_PORT.set(span, port(request));
      // TODO set resource name from URL.
    }
    return span;
  }

  public Span onResponse(final Span span, final RESPONSE response) {
    assert span != null;
    if (response != null) {
      final Integer status = status(response);
      if (status != null) {
        Tags.HTTP_STATUS.set(span, status);
        if (status >= 500) {
          Tags.ERROR.set(span, true);
        }
      }
    }
    return span;
  }

  //  @Override
  //  public Span onError(final Span span, final Throwable throwable) {
  //    assert span != null;
  //    // FIXME
  //    final Object status = span.getTag("http.status");
  //    if (status == null || status.equals(200)) {
  //      // Ensure status set correctly
  //      span.setTag("http.status", 500);
  //    }
  //    return super.onError(span, throwable);
  //  }
}
