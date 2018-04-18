package datadog.trace.instrumentation.ratpack.impl;

import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;
import ratpack.http.client.RequestSpec;

/**
 * SimpleTextMap to add headers to an outgoing Ratpack HttpClient request
 *
 * @see datadog.trace.instrumentation.apachehttpclient.DDTracingClientExec.HttpHeadersInjectAdapter
 */
public class RequestSpecInjectAdapter implements TextMap {
  private final RequestSpec requestSpec;

  public RequestSpecInjectAdapter(RequestSpec requestSpec) {
    this.requestSpec = requestSpec;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException("Should be used only with tracer#inject()");
  }

  @Override
  public void put(String key, String value) {
    requestSpec.getHeaders().add(key, value);
  }
}
