/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1;

import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.client.HttpAsyncClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApacheHttpAsyncClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final RequestConfig requestConfig =
      RequestConfig.custom()
          .setConnectTimeout((int) AbstractHttpClientTest.CONNECTION_TIMEOUT.toMillis())
          .build();

  private static final RequestConfig requestWithReadTimeoutConfig =
      RequestConfig.copy(requestConfig)
          .setSocketTimeout((int) AbstractHttpClientTest.READ_TIMEOUT.toMillis())
          .build();

  private CloseableHttpAsyncClient client;
  private CloseableHttpAsyncClient clientWithReadTimeout;

  // Use a fresh client per test: with httpasyncclient 4.1.5 a failing test can leave the
  // shared I/O reactor in a STOPPED state, cascading "Request execution cancelled" failures
  // into every subsequent test.
  @BeforeEach
  void setUp() {
    client = HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig).build();
    clientWithReadTimeout =
        HttpAsyncClients.custom().setDefaultRequestConfig(requestWithReadTimeoutConfig).build();
    cleanup.deferCleanup(client);
    cleanup.deferCleanup(clientWithReadTimeout);
    client.start();
    clientWithReadTimeout.start();
  }

  HttpAsyncClient getClient(URI uri) {
    if (uri.getPath().endsWith("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

  @Nested
  class ApacheClientUriRequestTest extends AbstractTest {

    @Override
    public HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      return configureRequest(new HttpUriRequest(method, uri), headers);
    }

    @Override
    public int sendRequest(
        HttpUriRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(getClient(uri).execute(request, null).get());
    }

    @Override
    public void sendRequestWithCallback(
        HttpUriRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        HttpClientResult httpClientResult) {
      getClient(uri).execute(request, responseCallback(httpClientResult));
    }
  }

  private static Stream<Arguments> requestsWithInetAddress() {
    return Stream.of(
        argumentSet("default port", false, false),
        argumentSet("explicit port", true, false),
        argumentSet("rewritten route", true, true));
  }

  @Nested
  class ApacheClientHostRequestTest extends AbstractTest {

    @ParameterizedTest
    @MethodSource(
        "io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1."
            + "ApacheHttpAsyncClientTest#requestsWithInetAddress")
    void requestWithInetAddress(boolean explicitPort, boolean rewrittenRoute) throws Exception {
      int serverPort = resolveAddress("/success").getPort();
      String hostname = rewrittenRoute ? "192.0.2.1" : "127.0.0.1";
      byte[] addressBytes =
          rewrittenRoute ? new byte[] {(byte) 192, 0, 2, 1} : new byte[] {127, 0, 0, 1};

      InetAddress address = InetAddress.getByAddress(hostname, addressBytes);
      int explicitTargetPort = rewrittenRoute ? 8080 : serverPort;
      HttpHost target = new HttpHost(address, explicitPort ? explicitTargetPort : -1, "http");
      URI expectedUri = URI.create(target.toURI() + "/success");

      HttpRoutePlanner routePlanner =
          rewrittenRoute
              ? (host, request, context) ->
                  new HttpRoute(new HttpHost("127.0.0.1", serverPort, "http"))
              : new DefaultRoutePlanner(host -> serverPort);

      CloseableHttpAsyncClient resolvedClient =
          HttpAsyncClients.custom()
              .setDefaultRequestConfig(requestConfig)
              .setRoutePlanner(routePlanner)
              .build();
      cleanup.deferCleanup(resolvedClient);
      resolvedClient.start();

      CompletableFuture<HttpResponse> callbackResult = new CompletableFuture<>();

      FutureCallback<HttpResponse> callback =
          new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse response) {
              testing.runWithSpan("callback", () -> {});
              callbackResult.complete(response);
            }

            @Override
            public void failed(Exception ex) {
              callbackResult.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
              callbackResult.completeExceptionally(new CancellationException());
            }
          };

      Future<HttpResponse> requestFuture =
          testing.runWithSpan(
              "parent",
              () ->
                  resolvedClient.execute(
                      target, new HttpUriRequest("GET", URI.create("/success")), callback));

      HttpResponse response = callbackResult.get(10, SECONDS);
      assertThat(getResponseCode(response)).isEqualTo(200);
      assertThat(requestFuture.get(10, SECONDS)).isSameAs(response);

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      assertClientSpan(span, expectedUri, "GET", 200, null)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(HTTP_REQUEST_METHOD, "GET"),
                              equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                              equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                              equalTo(SERVER_ADDRESS, hostname),
                              equalTo(SERVER_PORT, explicitPort ? explicitTargetPort : 80),
                              equalTo(URL_FULL, expectedUri.toString()),
                              equalTo(maybeStablePeerService(), "test-peer-service")),
                  span -> assertServerSpan(span).hasParent(trace.getSpan(1)),
                  span ->
                      span.hasName("callback")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))));
    }

    @Override
    public HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      return configureRequest(
          new HttpUriRequest(method, URI.create(fullPathFromUri(uri))), headers);
    }

    @Override
    public int sendRequest(
        HttpUriRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(
          getClient(uri)
              .execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request, null)
              .get());
    }

    @Override
    public void sendRequestWithCallback(
        HttpUriRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        HttpClientResult httpClientResult) {
      getClient(uri)
          .execute(
              new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
              request,
              responseCallback(httpClientResult));
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestTest extends AbstractTest {

    @Override
    public HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      return configureRequest(new HttpUriRequest(method, URI.create(uri.toString())), headers);
    }

    @Override
    public int sendRequest(
        HttpUriRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(
          getClient(uri)
              .execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request, null)
              .get());
    }

    @Override
    public void sendRequestWithCallback(
        HttpUriRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        HttpClientResult httpClientResult) {
      getClient(uri)
          .execute(
              new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
              request,
              responseCallback(httpClientResult));
    }
  }

  abstract static class AbstractTest extends AbstractHttpClientTest<HttpUriRequest> {

    @Override
    protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
      super.configure(optionsBuilder);
      optionsBuilder.spanEndsAfterBody();
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

  static FutureCallback<HttpResponse> responseCallback(HttpClientResult httpClientResult) {
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
        httpClientResult.complete(response.getStatusLine().getStatusCode());
      }

      @Override
      public void failed(Exception e) {
        httpClientResult.complete(e);
      }

      @Override
      public void cancelled() {
        httpClientResult.complete(new CancellationException());
      }
    };
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
