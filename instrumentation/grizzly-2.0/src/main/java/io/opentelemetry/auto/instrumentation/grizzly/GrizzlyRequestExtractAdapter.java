package io.opentelemetry.auto.instrumentation.grizzly;

import io.opentelemetry.context.propagation.HttpTextFormat;
import org.glassfish.grizzly.http.server.Request;

public class GrizzlyRequestExtractAdapter implements HttpTextFormat.Getter<Request> {

  public static final GrizzlyRequestExtractAdapter GETTER = new GrizzlyRequestExtractAdapter();

  @Override
  public String get(final Request carrier, final String key) {
    return carrier.getHeader(key);
  }
}
