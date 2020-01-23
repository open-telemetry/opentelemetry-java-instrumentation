package io.opentelemetry.auto.instrumentation.jaxrs.v1;

import io.opentelemetry.context.propagation.HttpTextFormat;
import javax.ws.rs.core.MultivaluedMap;

public final class InjectAdapter implements HttpTextFormat.Setter<MultivaluedMap> {

  public static final InjectAdapter SETTER = new InjectAdapter();

  @Override
  public void put(final MultivaluedMap headers, final String key, final String value) {
    // Don't allow duplicates.
    headers.putSingle(key, value);
  }
}
