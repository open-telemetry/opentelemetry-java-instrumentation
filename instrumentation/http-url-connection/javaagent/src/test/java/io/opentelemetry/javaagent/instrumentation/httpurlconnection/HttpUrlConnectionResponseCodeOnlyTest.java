/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.extension.RegisterExtension;

public class HttpUrlConnectionResponseCodeOnlyTest
    extends AbstractHttpClientTest<HttpURLConnection> {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Override
  public HttpURLConnection buildRequest(String method, URI uri, Map<String, String> headers)
      throws Exception {
    return (HttpURLConnection) uri.toURL().openConnection();
  }

  @Override
  public int sendRequest(
      HttpURLConnection connection, String method, URI uri, Map<String, String> headers)
      throws Exception {
    try {
      connection.setRequestMethod(method);
      connection.setConnectTimeout((int) CONNECTION_TIMEOUT.toMillis());
      if (uri.toString().contains("/read-timeout")) {
        connection.setReadTimeout((int) READ_TIMEOUT.toMillis());
      }

      headers.forEach(connection::setRequestProperty);
      connection.setRequestProperty("Connection", "close");
      return connection.getResponseCode();
    } finally {
      connection.disconnect();
    }
  }

  @Override
  public int maxRedirects() {
    return 20;
  }

  @Override
  public Integer responseCodeOnRedirectError() {
    return 302;
  }

  @Override
  public boolean testReusedRequest() {
    // HttpURLConnection can't be reused
    return false;
  }

  @Override
  public boolean testCallback() {
    return false;
  }

  @Override
  public boolean testReadTimeout() {
    return true;
  }
}
