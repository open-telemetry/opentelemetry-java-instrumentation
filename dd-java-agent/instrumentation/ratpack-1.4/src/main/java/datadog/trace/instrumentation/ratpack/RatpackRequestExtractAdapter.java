package datadog.trace.instrumentation.ratpack;

import com.google.common.collect.ListMultimap;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;
import ratpack.http.Request;

/**
 * Simple request extractor in the same vein as @see
 * io.opentracing.contrib.web.servlet.filter.HttpServletRequestExtractAdapter
 */
public class RatpackRequestExtractAdapter implements TextMap {
  private final ListMultimap<String, String> headers;

  RatpackRequestExtractAdapter(Request request) {
    this.headers = request.getHeaders().asMultiValueMap().asMultimap();
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return headers.entries().iterator();
  }

  @Override
  public void put(String key, String value) {
    throw new UnsupportedOperationException("This class should be used only with Tracer.inject()!");
  }
}
