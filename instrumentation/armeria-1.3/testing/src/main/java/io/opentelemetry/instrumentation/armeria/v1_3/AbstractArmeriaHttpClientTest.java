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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractArmeriaHttpClientTest extends AbstractHttpClientTest<HttpRequest> {

  protected abstract WebClientBuilder configureClient(WebClientBuilder clientBuilder);

  private AtomicBoolean decoratorCalled;

  private WebClient client;

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
  }

  @Override
  protected final HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    return HttpRequest.of(
        RequestHeaders.builder(HttpMethod.valueOf(method), uri.toString())
            .set(headers.entrySet())
            .build());
  }

  @Override
  protected final int sendRequest(
      HttpRequest request, String method, URI uri, Map<String, String> headers) {
    try {
      return client.execute(request).aggregate().join().status().code();
    } catch (CompletionException e) {
      return Exceptions.throwUnsafely(e.getCause());
    }
  }

  @Override
  protected final void sendRequestWithCallback(
      HttpRequest request,
      String method,
      URI uri,
      Map<String, String> headers,
      RequestResult requestResult) {
    client
        .execute(request)
        .aggregate()
        .whenComplete(
            (response, throwable) ->
                requestResult.complete(() -> response.status().code(), throwable));
  }

  @Override
  protected void configure(HttpClientTestOptions options) {
    // Not supported yet: https://github.com/line/armeria/issues/2489
    options.disableTestRedirects();
    // armeria requests can't be reused
    options.disableTestReusedRequest();

    Set<AttributeKey<?>> extra = new HashSet<>();
    extra.add(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH);
    extra.add(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH);
    extra.addAll(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
    options.setHttpAttributes(unused -> extra);
  }

  @Test
  void userDecoratorsNotClobbered() {
    client.get(resolveAddress("/success").toString()).aggregate().join();
    assertThat(decoratorCalled).isTrue();
  }
}
