/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javahttpclient;

import java.net.http.HttpClient;

class Http1ClientTest extends JavaHttpClientTest {

  @Override
  protected void configureHttpClientBuilder(HttpClient.Builder httpClientBuilder) {
    httpClientBuilder.version(HttpClient.Version.HTTP_1_1);
  }
}
