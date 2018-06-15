package datadog.trace.instrumentation.netty40.server;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;

public class NettyRequestExtractAdapter implements TextMap {
  private final HttpHeaders headers;

  NettyRequestExtractAdapter(final HttpRequest request) {
    this.headers = request.headers();
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return headers.iterator();
  }

  @Override
  public void put(final String key, final String value) {
    throw new UnsupportedOperationException("This class should be used only with Tracer.inject()!");
  }
}
