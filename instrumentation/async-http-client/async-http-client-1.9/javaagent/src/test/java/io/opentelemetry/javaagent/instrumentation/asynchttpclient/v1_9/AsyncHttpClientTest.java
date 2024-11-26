/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.uri.Uri;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.extension.RegisterExtension;

class AsyncHttpClientTest extends AbstractHttpClientTest<Request> {

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpClientInstrumentationExtension.forAgent();

  private static final int CONNECTION_TIMEOUT_MS = (int) CONNECTION_TIMEOUT.toMillis();
  private static final int READ_TIMEOUT_MS = (int) READ_TIMEOUT.toMillis();

  private static final AsyncHttpClient client = buildClient(false);
  private static final AsyncHttpClient clientWithReadTimeout = buildClient(true);

  private static AsyncHttpClient buildClient(boolean readTimeout) {
    AsyncHttpClientConfig.Builder builder =
        new AsyncHttpClientConfig.Builder().setConnectTimeout(CONNECTION_TIMEOUT_MS);
    if (readTimeout) {
      builder.setReadTimeout(READ_TIMEOUT_MS);
    }
    return new AsyncHttpClient(builder.build());
  }

  private static AsyncHttpClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
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
    optionsBuilder.spanEndsAfterBody();
    optionsBuilder.disableTestRedirects();

    // disable read timeout test for non latest because it is flaky with 1.9.0
    if (!Boolean.getBoolean("testLatestDeps")) {
      optionsBuilder.disableTestReadTimeout();
    }

    optionsBuilder.setHttpAttributes(
        endpoint -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(NETWORK_PROTOCOL_VERSION);
          return attributes;
        });
  }
}
