/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Map;
import org.asynchttpclient.Request;

class AsyncHttpClientCompletableFutureTest extends AsyncHttpClientTest {

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);

    optionsBuilder.setHasSendRequest(false);
  }

  @Override
  public int sendRequest(Request request, String method, URI uri, Map<String, String> headers) {
    throw new IllegalStateException("this test only tests requests with callback");
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult) {
    client
        .executeRequest(request)
        .toCompletableFuture()
        .whenComplete(
            (response, throwable) -> {
              if (throwable == null) {
                requestResult.complete(response.getStatusCode());
              } else {
                requestResult.complete(throwable);
              }
            });
  }
}
