package datadog.trace.instrumentation.http_url_connection;

import io.opentracing.propagation.TextMap;
import java.net.HttpURLConnection;
import java.util.Iterator;
import java.util.Map;

public class MessageHeadersInjectAdapter implements TextMap {

  private final HttpURLConnection connection;

  public MessageHeadersInjectAdapter(final HttpURLConnection connection) {
    this.connection = connection;
  }

  @Override
  public void put(final String key, final String value) {
    try {
      if (connection.getRequestProperty(key) == null) {
        connection.setRequestProperty(key, value);
      }
    } catch (final IllegalStateException e) {
      // Connection is already established. Too late to set headers.
    }
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException("This class should be used only with tracer#inject()");
  }
}
