package datadog.trace.instrumentation.servlet3;

import datadog.trace.instrumentation.api.AgentPropagation;
import javax.servlet.http.HttpServletRequest;

/** Inject into request attributes since the request headers can't be modified. */
public class HttpServletRequestInjectAdapter
    implements AgentPropagation.Setter<HttpServletRequest> {

  public static final HttpServletRequestInjectAdapter SETTER =
      new HttpServletRequestInjectAdapter();

  @Override
  public void set(final HttpServletRequest carrier, final String key, final String value) {
    carrier.setAttribute(key, value);
  }
}
