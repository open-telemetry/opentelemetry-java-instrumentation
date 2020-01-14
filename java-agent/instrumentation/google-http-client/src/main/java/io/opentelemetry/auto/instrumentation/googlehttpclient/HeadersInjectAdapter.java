package io.opentelemetry.auto.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import io.opentelemetry.auto.instrumentation.api.AgentPropagation;

public class HeadersInjectAdapter implements AgentPropagation.Setter<HttpRequest> {

  public static final HeadersInjectAdapter SETTER = new HeadersInjectAdapter();

  @Override
  public void set(final HttpRequest carrier, final String key, final String value) {
    carrier.getHeaders().put(key, value);
  }
}
