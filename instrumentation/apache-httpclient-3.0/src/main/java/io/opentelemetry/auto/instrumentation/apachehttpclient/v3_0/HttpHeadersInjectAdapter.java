package io.opentelemetry.auto.instrumentation.apachehttpclient.v3_0;

import io.opentelemetry.context.propagation.HttpTextFormat;
import org.apache.commons.httpclient.HttpMethod;

public class HttpHeadersInjectAdapter implements HttpTextFormat.Setter<HttpMethod> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void put(final HttpMethod carrier, final String key, final String value) {
    carrier.addRequestHeader(key, value);
  }
}
