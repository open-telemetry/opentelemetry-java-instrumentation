package io.opentelemetry.auto.instrumentation.http_url_connection;

import io.opentelemetry.context.propagation.HttpTextFormat;
import java.net.HttpURLConnection;

public class HeadersInjectAdapter implements HttpTextFormat.Setter<HttpURLConnection> {

  public static final HeadersInjectAdapter SETTER = new HeadersInjectAdapter();

  @Override
  public void put(final HttpURLConnection carrier, final String key, final String value) {
    carrier.setRequestProperty(key, value);
  }
}
