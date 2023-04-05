/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;

class GoogleHttpClientAsyncTest extends AbstractGoogleHttpClientTest {

  private final ExecutorService executor = Executors.newFixedThreadPool(4);

  @AfterAll
  void tearDown() {
    executor.shutdown();
  }

  @Override
  protected HttpResponse sendRequest(HttpRequest request) throws Exception {
    return request.executeAsync(executor).get();
  }
}
