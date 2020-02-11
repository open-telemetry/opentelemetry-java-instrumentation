package datadog.trace.instrumentation.http_url_connection;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import java.net.HttpURLConnection;

public class HeadersInjectAdapter implements AgentPropagation.Setter<HttpURLConnection> {

  public static final HeadersInjectAdapter SETTER = new HeadersInjectAdapter();

  @Override
  public void set(final HttpURLConnection carrier, final String key, final String value) {
    carrier.setRequestProperty(key, value);
  }
}
