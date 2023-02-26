/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.joddhttp.v4_2;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Map;
import jodd.http.HttpRequest;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JoddHttpTest extends AbstractHttpClientTest<HttpRequest> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Nullable
  @Override
  protected String userAgent() {
    return "Jodd HTTP";
  }

  @Override
  public HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpRequest request =
        new HttpRequest()
            .method(method)
            .set(uri.toString())
            .followRedirects(true)
            .connectionKeepAlive(true)
            .header("user-agent", userAgent());
    for (Map.Entry<String, String> header : headers.entrySet()) {
      request.headerOverwrite(header.getKey(), header.getValue());
    }
    if (uri.toString().contains("/read-timeout")) {
      request.timeout((int) READ_TIMEOUT.toMillis());
    }
    return request;
  }

  @Override
  public int sendRequest(HttpRequest request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    request.method(method).set(uri.toString());
    for (Map.Entry<String, String> header : headers.entrySet()) {
      request.headerOverwrite(header.getKey(), header.getValue());
    }
    return request.send().statusCode();
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.enableTestReadTimeout();
    optionsBuilder.disableTestCallback();
    // Circular Redirects are not explicitly handled by jodd-http
    optionsBuilder.disableTestCircularRedirects();
  }
}
