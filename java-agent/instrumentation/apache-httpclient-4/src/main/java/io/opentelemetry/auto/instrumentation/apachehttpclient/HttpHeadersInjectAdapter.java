package io.opentelemetry.auto.instrumentation.apachehttpclient;

import io.opentelemetry.auto.instrumentation.api.AgentPropagation;
import org.apache.http.client.methods.HttpUriRequest;

public class HttpHeadersInjectAdapter implements AgentPropagation.Setter<HttpUriRequest> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void set(final HttpUriRequest carrier, final String key, final String value) {
    carrier.addHeader(key, value);
  }
}
