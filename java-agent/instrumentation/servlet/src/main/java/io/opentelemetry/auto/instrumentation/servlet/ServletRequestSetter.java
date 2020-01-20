package io.opentelemetry.auto.instrumentation.servlet;

import io.opentelemetry.context.propagation.HttpTextFormat;

import javax.servlet.ServletRequest;

/** Inject into request attributes since the request headers can't be modified. */
public class ServletRequestSetter implements HttpTextFormat.Setter<ServletRequest> {
  public static final ServletRequestSetter SETTER = new ServletRequestSetter();

  @Override
  public void put(final ServletRequest carrier, final String key, final String value) {
    carrier.setAttribute(key, value);
  }
}
