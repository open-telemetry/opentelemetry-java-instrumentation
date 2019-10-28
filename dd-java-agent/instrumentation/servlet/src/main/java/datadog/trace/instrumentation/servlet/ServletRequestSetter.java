package datadog.trace.instrumentation.servlet;

import datadog.trace.instrumentation.api.AgentPropagation;
import javax.servlet.ServletRequest;

/** Inject into request attributes since the request headers can't be modified. */
public class ServletRequestSetter implements AgentPropagation.Setter<ServletRequest> {
  public static final ServletRequestSetter SETTER = new ServletRequestSetter();

  @Override
  public void set(final ServletRequest carrier, final String key, final String value) {
    carrier.setAttribute(key, value);
  }
}
