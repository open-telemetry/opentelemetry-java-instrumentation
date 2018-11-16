package datadog.trace.instrumentation.servlet3;

import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

/** Inject into request attributes since the request headers can't be modified. */
public class HttpServletRequestInjectAdapter implements TextMap {

  private final HttpServletRequest httpServletRequest;

  public HttpServletRequestInjectAdapter(final HttpServletRequest httpServletRequest) {
    this.httpServletRequest = httpServletRequest;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException(
        "This class should be used only with Tracer.extract()!");
  }

  @Override
  public void put(final String key, final String value) {
    httpServletRequest.setAttribute(key, value);
  }
}
