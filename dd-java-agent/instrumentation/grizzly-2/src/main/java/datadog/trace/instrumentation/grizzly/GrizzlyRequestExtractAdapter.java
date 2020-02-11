package datadog.trace.instrumentation.grizzly;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import org.glassfish.grizzly.http.server.Request;

public class GrizzlyRequestExtractAdapter implements AgentPropagation.Getter<Request> {

  public static final GrizzlyRequestExtractAdapter GETTER = new GrizzlyRequestExtractAdapter();

  @Override
  public Iterable<String> keys(final Request carrier) {
    return carrier.getHeaderNames();
  }

  @Override
  public String get(final Request carrier, final String key) {
    return carrier.getHeader(key);
  }
}
