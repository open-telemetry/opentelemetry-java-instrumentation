package datadog.trace.instrumentation.okhttp3;

import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;
import okhttp3.Request;

/**
 * Helper class to inject span context into request headers.
 *
 * @author Pavol Loffay
 */
public class RequestBuilderInjectAdapter implements TextMap {

  private final Request.Builder requestBuilder;

  public RequestBuilderInjectAdapter(final Request.Builder request) {
    this.requestBuilder = request;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException("Should be used only with tracer#inject()");
  }

  @Override
  public void put(final String key, final String value) {
    requestBuilder.addHeader(key, value);
  }
}
