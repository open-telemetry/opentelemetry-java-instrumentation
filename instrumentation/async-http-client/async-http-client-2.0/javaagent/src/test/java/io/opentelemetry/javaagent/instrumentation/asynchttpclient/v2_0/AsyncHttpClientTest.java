/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.uri.Uri;
import org.junit.jupiter.api.extension.RegisterExtension;

class AsyncHttpClientTest extends AbstractHttpClientTest<Request> {

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpClientInstrumentationExtension.forAgent();

  private static final int CONNECTION_TIMEOUT_MS = (int) CONNECTION_TIMEOUT.toMillis();

  // request timeout is needed in addition to connect timeout on async-http-client versions 2.1.0+
  private static final AsyncHttpClient client =
      Dsl.asyncHttpClient(
          Dsl.config()
              .setConnectTimeout(CONNECTION_TIMEOUT_MS)
              .setRequestTimeout(CONNECTION_TIMEOUT_MS));

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers) {
    RequestBuilder requestBuilder = new RequestBuilder(method).setUri(Uri.create(uri.toString()));
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      requestBuilder.setHeader(entry.getKey(), entry.getValue());
    }
    return requestBuilder.build();
  }

  @Override
  public int sendRequest(Request request, String method, URI uri, Map<String, String> headers)
      throws ExecutionException, InterruptedException {
    return client.executeRequest(request).get().getStatusCode();
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult) {
    client.executeRequest(
        request,
        new AsyncCompletionHandler<Void>() {
          @Override
          public Void onCompleted(Response response) {
            requestResult.complete(response.getStatusCode());
            return null;
          }

          @Override
          public void onThrowable(Throwable throwable) {
            requestResult.complete(throwable);
          }
        });
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestRedirects();
  }
}
