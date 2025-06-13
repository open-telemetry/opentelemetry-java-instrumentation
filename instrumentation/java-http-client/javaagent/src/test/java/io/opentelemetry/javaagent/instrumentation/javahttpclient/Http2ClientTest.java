/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javahttpclient;

import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.http.HttpClient;

class Http2ClientTest extends JavaHttpClientTest {

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
          if (uriString.equals("http://localhost:61/") || uriString.equals("https://192.0.2.1/")) {
            return "1.1";
          }
          return "2";
        });
  }
}
