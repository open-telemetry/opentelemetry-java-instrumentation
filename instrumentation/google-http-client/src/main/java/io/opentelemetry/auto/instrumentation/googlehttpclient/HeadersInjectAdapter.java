package io.opentelemetry.auto.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import io.opentelemetry.context.propagation.HttpTextFormat;

public class HeadersInjectAdapter implements HttpTextFormat.Setter<HttpRequest> {

  public static final HeadersInjectAdapter SETTER = new HeadersInjectAdapter();

  @Override
  public void put(final HttpRequest carrier, final String key, final String value) {
    carrier.getHeaders().put(key, value);
  }
}
