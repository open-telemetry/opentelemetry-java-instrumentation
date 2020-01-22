package io.opentelemetry.auto.instrumentation.servlet3;

import io.opentelemetry.context.propagation.HttpTextFormat;
import javax.servlet.http.HttpServletRequest;

/** Inject into request attributes since the request headers can't be modified. */
public class HttpServletRequestInjectAdapter implements HttpTextFormat.Setter<HttpServletRequest> {

  public static final HttpServletRequestInjectAdapter SETTER =
      new HttpServletRequestInjectAdapter();

  @Override
  public void put(final HttpServletRequest carrier, final String key, final String value) {
    carrier.setAttribute(key, value);
  }
}
