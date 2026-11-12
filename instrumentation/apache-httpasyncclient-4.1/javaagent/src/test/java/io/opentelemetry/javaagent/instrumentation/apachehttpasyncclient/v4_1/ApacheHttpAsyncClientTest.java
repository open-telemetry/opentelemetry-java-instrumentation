/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.instrumentation.testing.util.TestServerServiceMapping.expectedMappedServiceName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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

  @Nested
  class ApacheClientHostRequestTest extends AbstractTest {

    @Test
    void redirectToUnavailablePortRetainsLastNetworkPeer() throws Exception {
      URI target = URI.create("http://127.0.0.1:" + PortUtils.UNUSABLE_PORT + "/success");

      HttpServer redirectServer =
          HttpServer.create(
              new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
      cleanup.deferCleanup(() -> redirectServer.stop(0));
      redirectServer.createContext(
          "/redirect",
          exchange -> {
            try {
              exchange.getResponseHeaders().set("Location", target.toString());
              exchange.sendResponseHeaders(302, -1);
            } finally {
              exchange.close();
            }
          });
      redirectServer.start();

      int redirectPort = redirectServer.getAddress().getPort();
      URI uri = URI.create("http://127.0.0.1:" + redirectPort + "/redirect");

      Throwable failure =
          catchThrowable(() -> client.execute(new HttpGet(uri), null).get(10, SECONDS));
      assertThat(failure)
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(ConnectException.class);
      Throwable cause = failure.getCause();

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("GET")
                          .hasKind(CLIENT)
                          .hasNoParent()
                          .hasStatus(StatusData.error())
                          .hasAttributesSatisfyingExactly(
                              equalTo(HTTP_REQUEST_METHOD, "GET"),
                              equalTo(HTTP_RESPONSE_STATUS_CODE, 302),
                              equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                              equalTo(SERVER_ADDRESS, uri.getHost()),
                              equalTo(SERVER_PORT, uri.getPort()),
                              equalTo(URL_FULL, uri.toString()),
                              equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                              equalTo(NETWORK_PEER_PORT, redirectPort),
                              equalTo(ERROR_TYPE, cause.getClass().getName()),
                              equalTo(
                                  stringKey("server.service.name"), expectedMappedServiceName()))));
    }

    @Test
    void connectionFailureOmitsNetworkPeer() {
      URI uri = URI.create("http://127.0.0.1:" + PortUtils.UNUSABLE_PORT + "/success");

      Throwable failure =
          catchThrowable(() -> client.execute(new HttpGet(uri), null).get(10, SECONDS));
      assertThat(failure)
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(ConnectException.class);
      Throwable cause = failure.getCause();

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("GET")
                          .hasKind(CLIENT)
                          .hasNoParent()
                          .hasStatus(StatusData.error())
                          .hasAttributesSatisfyingExactly(
                              equalTo(HTTP_REQUEST_METHOD, "GET"),
                              equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                              equalTo(SERVER_ADDRESS, uri.getHost()),
                              equalTo(SERVER_PORT, uri.getPort()),
                              equalTo(URL_FULL, uri.toString()),
                              equalTo(ERROR_TYPE, cause.getClass().getName()),
                              equalTo(
                                  stringKey("server.service.name"), expectedMappedServiceName()))));
    }

    @Test
    void requestWithMinimalClientAndNullContext() throws Exception {
      URI uri = resolveAddress("/success");
      CloseableHttpAsyncClient minimalClient = HttpAsyncClients.createMinimal();
      cleanup.deferCleanup(minimalClient);
      minimalClient.start();

      HttpResponse response =
          minimalClient
              .execute(
                  HttpAsyncMethods.create(
                      new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
                      new HttpGet(uri.getRawPath())),
                  new BasicAsyncResponseConsumer(),
                  null,
                  null)
              .get(10, SECONDS);

      assertThat(getResponseCode(response)).isEqualTo(200);

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> assertSuccessfulPeerSpan(span, uri),
                  span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
    }

    @Test
    void redirectToDifferentPort() throws Exception {
      URI target = resolveAddress("/success");

      HttpServer redirectServer =
          HttpServer.create(
              new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
      cleanup.deferCleanup(() -> redirectServer.stop(0));

      redirectServer.createContext(
          "/redirect",
          exchange -> {
            try {
              exchange.getResponseHeaders().set("Location", target.toString());
              exchange.sendResponseHeaders(302, -1);
            } finally {
              exchange.close();
            }
          });
      redirectServer.start();

      int redirectPort = redirectServer.getAddress().getPort();
      assertThat(redirectPort).isNotEqualTo(target.getPort());
      URI uri = URI.create("http://127.0.0.1:" + redirectPort + "/redirect");

      HttpResponse response = client.execute(new HttpGet(uri), null).get(10, SECONDS);
      assertThat(getResponseCode(response)).isEqualTo(200);

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> assertSuccessfulPeerSpan(span, uri),
                  span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
    }

    private SpanDataAssert assertSuccessfulPeerSpan(SpanDataAssert span, URI uri) {
      return assertClientSpan(span, uri, "GET", 200, null)
          .hasNoParent()
          .hasStatus(StatusData.unset())
          .hasAttributesSatisfying(
              satisfies(NETWORK_PEER_ADDRESS, val -> val.isNotNull()),
              satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()));
    }

    @Override
    protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
      super.configure(optionsBuilder);
      optionsBuilder.setTestProxy(true).setExpectProxyNetworkPeerAttributes(true);
    }

    @Override
    public int sendRequestWithProxy(
        HttpUriRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        InetSocketAddress proxyAddress)
        throws Exception {
      String host = uri.getHost();
      byte[] addressBytes = InetAddress.getByName(host).getAddress();

      // Store the host to avoid reverse DNS when HttpCore 4.4 calls InetAddress.getHostName().
      InetAddress targetAddress = InetAddress.getByAddress(host, addressBytes);

      // Use an explicit destination IP to catch it being incorrectly reported as the network peer.
      HttpHost target = new HttpHost(targetAddress, uri.getPort(), uri.getScheme());

      HttpClientContext context = HttpClientContext.create();
      context.setRequestConfig(
          RequestConfig.copy(requestWithReadTimeoutConfig)
              .setProxy(
                  new HttpHost(proxyAddress.getAddress().getHostAddress(), proxyAddress.getPort()))
              .build());

      return getResponseCode(
          getClient(uri).execute(target, request, context, null).get(10, SECONDS));
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
