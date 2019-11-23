package datadog.trace.instrumentation.playws2;

import datadog.trace.instrumentation.api.AgentPropagation;
import play.shaded.ahc.org.asynchttpclient.Request;

public class HeadersInjectAdapter implements AgentPropagation.Setter<Request> {

  public static final HeadersInjectAdapter SETTER = new HeadersInjectAdapter();

  @Override
  public void set(final Request carrier, final String key, final String value) {
    carrier.getHeaders().add(key, value);
  }
}
