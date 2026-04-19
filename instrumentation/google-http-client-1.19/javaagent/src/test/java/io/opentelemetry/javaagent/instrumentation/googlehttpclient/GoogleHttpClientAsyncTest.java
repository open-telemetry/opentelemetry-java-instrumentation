/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class GoogleHttpClientAsyncTest extends AbstractGoogleHttpClientTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final ExecutorService executor = Executors.newFixedThreadPool(4);

  @BeforeAll
  void setUpExecutor() {
    cleanup.deferAfterAll(executor::shutdown);
  }

  @Override
  protected HttpResponse sendRequest(HttpRequest request) throws Exception {
    return request.executeAsync(executor).get();
  }
}
