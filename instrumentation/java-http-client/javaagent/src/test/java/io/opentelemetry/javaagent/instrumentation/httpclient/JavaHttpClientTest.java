/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.instrumentation.httpclient.AbstractJavaHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class JavaHttpClientTest extends AbstractJavaHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Override
  protected HttpClient configureHttpClient(HttpClient httpClient) {
    return httpClient;
  }

  @Nested
  static class Http1ClientTest extends JavaHttpClientTest {

    @Override
    protected void configureHttpClientBuilder(HttpClient.Builder httpClientBuilder) {
      httpClientBuilder.version(HttpClient.Version.HTTP_1_1);
    }
  }

  @Nested
  static class Http2ClientTest extends JavaHttpClientTest {

    @Override
    protected void configureHttpClientBuilder(HttpClient.Builder httpClientBuilder) {
      httpClientBuilder.version(HttpClient.Version.HTTP_2);
    }

    @Override
    protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
      super.configure(optionsBuilder);

      optionsBuilder.setHttpProtocolVersion(
          uri -> {
            String uriString = uri.toString();
            if (uriString.equals("http://localhost:61/")
                || uriString.equals("https://192.0.2.1/")) {
              return "1.1";
            }
            return "2";
          });
    }
  }
}
