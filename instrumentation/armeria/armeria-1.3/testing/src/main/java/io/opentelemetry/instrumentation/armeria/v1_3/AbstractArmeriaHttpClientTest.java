/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.WebClientBuilder;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.util.Exceptions;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractArmeriaHttpClientTest extends AbstractHttpClientTest<HttpRequest> {

  protected abstract WebClientBuilder configureClient(WebClientBuilder clientBuilder);

  private AtomicBoolean decoratorCalled;

  private WebClient client;
  private WebClient clientWithReadTimeout;

  @BeforeEach
  void setupClient() {
    decoratorCalled = new AtomicBoolean();
    client =
        configureClient(
                WebClient.builder()
                    .decorator(
                        (delegate, ctx, req) -> {
                          decoratorCalled.set(true);
                          return delegate.execute(ctx, req);
                        })
                    .factory(ClientFactory.builder().connectTimeout(connectTimeout()).build()))
            .build();
    clientWithReadTimeout =
        configureClient(
                WebClient.builder()
                    .responseTimeout(READ_TIMEOUT)
                    .factory(ClientFactory.builder().connectTimeout(connectTimeout()).build()))
            .build();
  }

  @Override
  public HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    return HttpRequest.of(
        RequestHeaders.builder()
            .method(HttpMethod.valueOf(method))
            .scheme(uri.getScheme())
            .authority(uri.getAuthority())
            .path(pathAndQuery(uri))
            .set(headers.entrySet())
            .build());
  }

  private static String pathAndQuery(URI uri) {
    String path = uri.getPath();
    String query = uri.getQuery();
    return (path == null ? "" : path) + (query == null ? "" : "?" + query);
  }

  @Override
  public int sendRequest(HttpRequest request, String method, URI uri, Map<String, String> headers) {
    try {
      return getClient(uri).execute(request).aggregate().join().status().code();
    } catch (CompletionException e) {
      return Exceptions.throwUnsafely(e.getCause());
    }
  }

  @Override
  public final void sendRequestWithCallback(
      HttpRequest request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    getClient(uri)
        .execute(request)
        .aggregate()
        .whenComplete(
            (response, throwable) ->
                httpClientResult.complete(() -> response.status().code(), throwable));
  }

  private WebClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    // Not supported yet: https://github.com/line/armeria/issues/2489
    optionsBuilder.disableTestRedirects();
    // armeria requests can't be reused
    optionsBuilder.disableTestReusedRequest();
    // can only use methods from HttpMethod enum
    optionsBuilder.disableTestNonStandardHttpMethod();
    optionsBuilder.spanEndsAfterBody();
    // armeria uses http/2
    optionsBuilder.setHttpProtocolVersion(
        uri -> {
          String uriString = uri.toString();
          if (uriString.equals("http://localhost:61/") || uriString.equals("https://192.0.2.1/")) {
            return "1.1";
          }
          return "2";
        });
  }

  @Test
  void userDecoratorsNotClobbered() {
    client.get(resolveAddress("/success").toString()).aggregate().join();
    assertThat(decoratorCalled).isTrue();
  }
}
