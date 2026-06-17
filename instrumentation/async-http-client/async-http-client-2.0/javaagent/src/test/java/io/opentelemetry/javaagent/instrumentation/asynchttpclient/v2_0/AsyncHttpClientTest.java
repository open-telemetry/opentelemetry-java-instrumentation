/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.uri.Uri;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;

class AsyncHttpClientTest extends AbstractHttpClientTest<Request> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private static final int CONNECTION_TIMEOUT_MS = (int) CONNECTION_TIMEOUT.toMillis();
  private static final int READ_TIMEOUT_MS = (int) READ_TIMEOUT.toMillis();

  private static final AsyncHttpClient client = buildClient(false);
  private static final AsyncHttpClient clientWithReadTimeout = buildClient(true);

  private static AsyncHttpClient buildClient(boolean readTimeout) {
    return Dsl.asyncHttpClient(configureTimeout(Dsl.config(), readTimeout));
  }

  protected static AsyncHttpClient getClient(URI uri) {
    if (uri.getPath().endsWith("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

  private static DefaultAsyncHttpClientConfig.Builder configureTimeout(
      DefaultAsyncHttpClientConfig.Builder builder, boolean readTimeout) {
    setTimeout(builder, "setConnectTimeout", CONNECTION_TIMEOUT_MS);
    if (readTimeout) {
      setTimeout(builder, "setRequestTimeout", READ_TIMEOUT_MS);
    }
    return builder;
  }

  private static void setTimeout(
      DefaultAsyncHttpClientConfig.Builder builder, String methodName, int timeout) {
    try {
      Method method =
          builder.getClass().getMethod(methodName, testLatestDeps() ? Duration.class : int.class);
      method.invoke(builder, testLatestDeps() ? Duration.ofMillis(timeout) : timeout);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to set timeout " + methodName, e);
    }
  }

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
    return getClient(uri).executeRequest(request).get().getStatusCode();
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult) {
    getClient(uri)
        .executeRequest(
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
    optionsBuilder.spanEndsAfterBody();
    // On Windows, non-routable addresses timeout instead of failing fast, causing test timeout
    // before the HTTP client can create a span, resulting in only the parent span.
    if (OS.WINDOWS.isCurrentOs()) {
      optionsBuilder.disableTestRemoteConnection();
    }
  }
}
