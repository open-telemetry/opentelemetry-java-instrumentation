package io.opentelemetry.auto.instrumentation.apachehttpasyncclient;

import io.opentelemetry.context.propagation.HttpTextFormat;
import org.apache.http.HttpRequest;

public class HttpHeadersInjectAdapter implements HttpTextFormat.Setter<HttpRequest> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void put(final HttpRequest carrier, final String key, final String value) {
    carrier.setHeader(key, value);
  }
}
