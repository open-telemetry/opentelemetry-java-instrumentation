/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.client.HttpAsyncClient;
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
      RequestConfig.custom().setConnectTimeout((int) Duration.ofSeconds(5).toMillis()).build();

  private final RequestConfig requestWithReadTimeoutConfig =
      RequestConfig.copy(requestConfig)
          .setSocketTimeout((int) Duration.ofSeconds(2).toMillis())
          .build();

  private final CloseableHttpAsyncClient client =
      HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig).build();
  private final CloseableHttpAsyncClient clientWithReadTimeout =
      HttpAsyncClients.custom().setDefaultRequestConfig(requestWithReadTimeoutConfig).build();

  @BeforeAll
  void setUp() {
    client.start();
    clientWithReadTimeout.start();
  }

  @AfterAll
  void tearDown() throws Exception {
    client.close();
    clientWithReadTimeout.close();
  }

  HttpAsyncClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

  @Nested
  class ApacheClientUriRequestTest extends AbstractHttpClientTest<HttpUriRequest> {

    @Override
    protected HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      return configureRequest(new HttpUriRequest(method, uri), headers);
    }

    @Override
    protected int sendRequest(
        HttpUriRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(getClient(uri).execute(request, null).get());
    }

    @Override
    protected void sendRequestWithCallback(
        HttpUriRequest request,
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
  class ApacheClientHostRequestTest extends AbstractHttpClientTest<HttpUriRequest> {

    @Override
    protected HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      return configureRequest(
          new HttpUriRequest(method, URI.create(fullPathFromUri(uri))), headers);
    }

    @Override
    protected int sendRequest(
        HttpUriRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(
          getClient(uri)
              .execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request, null)
              .get());
    }

    @Override
    protected void sendRequestWithCallback(
        HttpUriRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        RequestResult requestResult) {
      getClient(uri)
          .execute(
              new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
              request,
              responseCallback(requestResult));
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      configureTest(options);
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestTest extends AbstractHttpClientTest<HttpUriRequest> {

    @Override
    protected HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      return configureRequest(new HttpUriRequest(method, URI.create(uri.toString())), headers);
    }

    @Override
    protected int sendRequest(
        HttpUriRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(
          getClient(uri)
              .execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request, null)
              .get());
    }

    @Override
    protected void sendRequestWithCallback(
        HttpUriRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        RequestResult requestResult) {
      getClient(uri)
          .execute(
              new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
              request,
              responseCallback(requestResult));
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      configureTest(options);
    }
  }

  HttpUriRequest configureRequest(HttpUriRequest request, Map<String, String> headers) {
    request.addHeader("user-agent", "httpasyncclient");
    headers.forEach((key, value) -> request.setHeader(new BasicHeader(key, value)));
    return request;
  }

  int getResponseCode(HttpResponse response) {
    try {
      if (response.getEntity() != null && response.getEntity().getContent() != null) {
        response.getEntity().getContent().close();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return response.getStatusLine().getStatusCode();
  }

  static FutureCallback<HttpResponse> responseCallback(
      AbstractHttpClientTest.RequestResult requestResult) {
    return new FutureCallback<HttpResponse>() {
      @Override
      public void completed(HttpResponse response) {
        try {
          if (response.getEntity() != null && response.getEntity().getContent() != null) {
            response.getEntity().getContent().close();
          }
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
        requestResult.complete(response.getStatusLine().getStatusCode());
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
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.add(SemanticAttributes.HTTP_SCHEME);
          attributes.add(SemanticAttributes.HTTP_TARGET);
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
