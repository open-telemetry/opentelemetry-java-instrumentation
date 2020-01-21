package io.opentelemetry.auto.instrumentation.springwebflux.client;

import io.opentelemetry.auto.instrumentation.api.AgentPropagation;
import org.springframework.http.HttpHeaders;

public class HttpHeadersInjectAdapter implements AgentPropagation.Setter<HttpHeaders> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void set(final HttpHeaders carrier, final String key, final String value) {
    carrier.set(key, value);
  }
}
