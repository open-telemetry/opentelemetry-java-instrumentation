package datadog.trace.instrumentation.ratpack.impl;

import io.opentracing.propagation.TextMap;
import java.util.*;
import ratpack.http.Request;
import ratpack.util.MultiValueMap;

/**
 * Simple request extractor in the same vein as @see
 * io.opentracing.contrib.web.servlet.filter.HttpServletRequestExtractAdapter
 */
public class RatpackRequestExtractAdapter implements TextMap {
  private final MultiValueMap<String, String> headers;

  RatpackRequestExtractAdapter(Request request) {
    this.headers = request.getHeaders().asMultiValueMap();
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return headers.entrySet().iterator();
  }

  @Override
  public void put(String key, String value) {
    throw new UnsupportedOperationException("This class should be used only with Tracer.inject()!");
  }
}
