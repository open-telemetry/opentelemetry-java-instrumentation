/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.async;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApacheHttpAsyncClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  // TODO(anuraaga): AbstractHttpClientTest should provide timeout values statically
  private final RequestConfig requestConfig =
      RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(5)).build();

  private final RequestConfig requestWithReadTimeoutConfig =
      RequestConfig.copy(requestConfig).setResponseTimeout(Timeout.ofSeconds(2)).build();

  private final CloseableHttpAsyncClient client =
      HttpAsyncClients.custom()
          .setDefaultRequestConfig(requestConfig)
          .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
          .build();
  private final CloseableHttpAsyncClient clientWithReadTimeout =
      HttpAsyncClients.custom()
          .setDefaultRequestConfig(requestWithReadTimeoutConfig)
          .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
          .build();

  @BeforeAll
  void setUp() {
    client.start();
    clientWithReadTimeout.start();
  }

  @AfterAll
  void tearDown() {
    client.close(CloseMode.GRACEFUL);
    clientWithReadTimeout.close(CloseMode.GRACEFUL);
  }

  CloseableHttpAsyncClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

  @Nested
  class ApacheClientUriRequestTest extends AbstractHttpClientTest<SimpleHttpRequest> {

    @Override
    protected SimpleHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      return configureRequest(new SimpleHttpRequest(method, uri), headers);
    }

    @Override
    protected int sendRequest(
        SimpleHttpRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(getClient(uri).execute(request, null).get());
    }

    @Override
    protected void sendRequestWithCallback(
        SimpleHttpRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        RequestResult requestResult) {
      getClient(uri).execute(request, responseCallback(requestResult));
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      configureTest(options);
    }
  }

  @Nested
  class ApacheClientHostRequestTest extends AbstractHttpClientTest<SimpleHttpRequest> {

    @Override
    protected SimpleHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      HttpHost httpHost = new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
      return configureRequest(
          new SimpleHttpRequest(method, httpHost, fullPathFromUri(uri)), headers);
    }

    @Override
    protected int sendRequest(
        SimpleHttpRequest request, String method, URI uri, Map<String, String> headers)
        throws ExecutionException, InterruptedException {
      return getResponseCode(getClient(uri).execute(request, null).get());
    }

    @Override
    protected void sendRequestWithCallback(
        SimpleHttpRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        RequestResult requestResult) {
      getClient(uri).execute(request, responseCallback(requestResult));
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      configureTest(options);
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestTest extends AbstractHttpClientTest<SimpleHttpRequest> {

    @Override
    protected SimpleHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      return configureRequest(new SimpleHttpRequest(method, uri), headers);
    }

    @Override
    protected int sendRequest(
        SimpleHttpRequest request, String method, URI uri, Map<String, String> headers)
        throws ExecutionException, InterruptedException {
      return getResponseCode(getClient(uri).execute(request, null).get());
    }

    @Override
    protected void sendRequestWithCallback(
        SimpleHttpRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        RequestResult requestResult) {
      getClient(uri).execute(request, responseCallback(requestResult));
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      configureTest(options);
    }
  }

  SimpleHttpRequest configureRequest(SimpleHttpRequest request, Map<String, String> headers) {
    request.addHeader("user-agent", "httpasyncclient");
    headers.forEach((key, value) -> request.setHeader(new BasicHeader(key, value)));
    return request;
  }

  int getResponseCode(SimpleHttpResponse response) {
    return response.getCode();
  }

  static FutureCallback<SimpleHttpResponse> responseCallback(
      AbstractHttpClientTest.RequestResult requestResult) {
    return new FutureCallback<>() {
      @Override
      public void completed(SimpleHttpResponse response) {
        requestResult.complete(response.getCode());
      }

      @Override
      public void failed(Exception e) {
        requestResult.complete(e);
      }

      @Override
      public void cancelled() {
        requestResult.complete(new CancellationException());
      }
    };
  }

  void configureTest(HttpClientTestOptions options) {
    options.setUserAgent("httpasyncclient");
    options.setResponseCodeOnRedirectError(302);
    options.enableTestReadTimeout();
    options.setHttpAttributes(
        endpoint -> {
          Set<AttributeKey<?>> attributes = new HashSet<>();
          attributes.add(SemanticAttributes.NET_PEER_NAME);
          attributes.add(SemanticAttributes.NET_PEER_PORT);
          attributes.add(SemanticAttributes.HTTP_URL);
          attributes.add(SemanticAttributes.HTTP_METHOD);
          if (endpoint.toString().contains("/success")) {
            attributes.add(SemanticAttributes.HTTP_FLAVOR);
          }
          attributes.add(SemanticAttributes.HTTP_USER_AGENT);
          return attributes;
        });
  }

  static String fullPathFromUri(URI uri) {
    StringBuilder builder = new StringBuilder();
    if (uri.getPath() != null) {
      builder.append(uri.getPath());
    }

    if (uri.getQuery() != null) {
      builder.append('?');
      builder.append(uri.getQuery());
    }

    if (uri.getFragment() != null) {
      builder.append('#');
      builder.append(uri.getFragment());
    }
    return builder.toString();
  }
}
