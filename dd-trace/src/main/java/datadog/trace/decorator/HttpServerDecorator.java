package datadog.trace.decorator;

import datadog.trace.tracer.Span;

public abstract class HttpServerDecorator<REQUEST, RESPONSE> extends ServerDecorator {

  protected abstract String method(REQUEST request);

  protected abstract String url(REQUEST request);

  protected abstract String hostname(REQUEST request);

  protected abstract int port(REQUEST request);

  protected abstract int status(RESPONSE response);

  @Override
  protected String spanType() {
    return "web";
  }

  public Span onRequest(final Span span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      span.setMeta("http.method", method(request));
      span.setMeta("http.url", url(request));
      span.setMeta("peer.hostname", hostname(request));
      span.setMeta("peer.port", port(request));
      // TODO set resource name from URL.
    }
    return span;
  }

  public Span onResponse(final Span span, final RESPONSE response) {
    assert span != null;
    if (response != null) {
      span.setMeta("http.status", status(response));
      if (status(response) >= 500) {
        span.setErrored(true);
      }
    }
    return span;
  }

  @Override
  public Span onError(final Span span, final Throwable throwable) {
    assert span != null;
    final Object status = span.getMeta("http.status");
    if (status == null || status.equals(200)) {
      // Ensure status set correctly
      span.setMeta("http.status", 500);
    }
    return super.onError(span, throwable);
  }
}
