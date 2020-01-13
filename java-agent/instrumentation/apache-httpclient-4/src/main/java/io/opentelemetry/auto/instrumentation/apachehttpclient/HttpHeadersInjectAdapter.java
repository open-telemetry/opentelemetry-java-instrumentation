package io.opentelemetry.auto.instrumentation.apachehttpclient;

import io.opentelemetry.context.propagation.HttpTextFormat;
import org.apache.http.client.methods.HttpUriRequest;

public class HttpHeadersInjectAdapter implements HttpTextFormat.Setter<HttpUriRequest> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void put(final HttpUriRequest carrier, final String key, final String value) {
    carrier.addHeader(key, value);
  }
}
