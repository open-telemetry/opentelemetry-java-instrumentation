package datadog.trace.instrumentation.jaxrs.v1;

import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;
import javax.ws.rs.core.MultivaluedMap;

public final class InjectAdapter implements TextMap {
  private final MultivaluedMap<String, Object> map;

  public InjectAdapter(final MultivaluedMap<String, Object> map) {
    this.map = map;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException(
        "InjectAdapter should only be used with Tracer.inject()");
  }

  @Override
  public void put(final String key, final String value) {
    // Don't allow duplicates.
    map.putSingle(key, value);
  }
}
