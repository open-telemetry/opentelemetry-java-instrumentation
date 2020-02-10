package datadog.trace.instrumentation.apachehttpasyncclient;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import org.apache.http.HttpRequest;

public class HttpHeadersInjectAdapter implements AgentPropagation.Setter<HttpRequest> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void set(final HttpRequest carrier, final String key, final String value) {
    carrier.setHeader(key, value);
  }
}
