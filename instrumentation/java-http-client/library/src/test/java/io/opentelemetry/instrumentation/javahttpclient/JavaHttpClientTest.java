/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpclient;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

class JavaHttpClientTest {

  abstract static class AbstractTest extends AbstractJavaHttpClientTest {
    @RegisterExtension
    static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

    @Override
    protected HttpClient configureHttpClient(HttpClient httpClient) {
      return JavaHttpClientTelemetry.builder(testing.getOpenTelemetry())
          .setCapturedRequestHeaders(singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
          .setCapturedResponseHeaders(singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
          .build()
          .wrap(httpClient);
    }
  }

  @Nested
  class Http1ClientTest extends AbstractTest {

    @Override
    protected void configureHttpClientBuilder(HttpClient.Builder httpClientBuilder) {
      httpClientBuilder.version(HttpClient.Version.HTTP_1_1);
    }
  }

  @Nested
  class Http2ClientTest extends AbstractTest {

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
