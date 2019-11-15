package datadog.trace.instrumentation.jaxrs;

import datadog.trace.instrumentation.api.AgentPropagation;
import javax.ws.rs.core.MultivaluedMap;

public final class InjectAdapter implements AgentPropagation.Setter<MultivaluedMap> {

  public static final InjectAdapter SETTER = new InjectAdapter();

  @Override
  public void set(final MultivaluedMap headers, final String key, final String value) {
    // Don't allow duplicates.
    headers.putSingle(key, value);
  }
}
