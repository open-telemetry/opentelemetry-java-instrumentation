package datadog.trace.instrumentation.jaxrs;

import datadog.trace.instrumentation.api.AgentPropagation;
import javax.ws.rs.client.ClientRequestContext;

public final class InjectAdapter implements AgentPropagation.Setter<ClientRequestContext> {

  public static final InjectAdapter SETTER = new InjectAdapter();

  @Override
  public void set(final ClientRequestContext carrier, final String key, final String value) {
    // Don't allow duplicates.
    carrier.getHeaders().putSingle(key, value);
  }
}
