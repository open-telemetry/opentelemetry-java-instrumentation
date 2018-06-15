package datadog.trace.instrumentation.netty40.client;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;

public class NettyResponseInjectAdapter implements TextMap {
  private final HttpHeaders headers;

  NettyResponseInjectAdapter(final HttpRequest request) {
    this.headers = request.headers();
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
