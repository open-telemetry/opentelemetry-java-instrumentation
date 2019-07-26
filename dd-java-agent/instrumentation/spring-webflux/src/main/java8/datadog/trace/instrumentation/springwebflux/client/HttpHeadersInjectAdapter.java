package datadog.trace.instrumentation.springwebflux.client;

import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;
import org.springframework.http.HttpHeaders;

public class HttpHeadersInjectAdapter implements TextMap {

  private final HttpHeaders headers;

  public HttpHeadersInjectAdapter(final HttpHeaders headers) {
    this.headers = headers;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException("This class should be used only with Tracer.inject()!");
  }

  @Override
  public void put(final String key, final String value) {
    headers.set(key, value);
  }
}
