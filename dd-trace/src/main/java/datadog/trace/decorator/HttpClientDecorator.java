package datadog.trace.decorator;

import datadog.trace.tracer.Span;

public abstract class HttpClientDecorator<REQUEST, RESPONSE> extends ClientDecorator {

  protected abstract String method(REQUEST request);

  protected abstract String url(REQUEST request);

  protected abstract String hostname(RESPONSE request);

  protected abstract int port(RESPONSE request);

  protected abstract int status(RESPONSE response);

  @Override
  protected String spanType() {
    return "http";
  }

  public Span onRequest(final Span span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      span.setMeta("http.method", method(request));
      span.setMeta("http.url", url(request));
    }
    return span;
  }

  public Span onResponse(final Span span, final RESPONSE response) {
    assert span != null;
    if (response != null) {
      span.setMeta("peer.hostname", hostname(response));
      span.setMeta("peer.port", port(response));

      final int status = status(response);
      span.setMeta("http.status", status);
      if (400 <= status && status < 500) {
        span.setErrored(true);
      }
    }
    return span;
  }
}
