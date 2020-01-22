package io.opentelemetry.auto.instrumentation.springwebflux.client;

import io.opentelemetry.context.propagation.HttpTextFormat;
import org.springframework.http.HttpHeaders;

public class HttpHeadersInjectAdapter implements HttpTextFormat.Setter<HttpHeaders> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void put(final HttpHeaders carrier, final String key, final String value) {
    carrier.set(key, value);
  }
}
