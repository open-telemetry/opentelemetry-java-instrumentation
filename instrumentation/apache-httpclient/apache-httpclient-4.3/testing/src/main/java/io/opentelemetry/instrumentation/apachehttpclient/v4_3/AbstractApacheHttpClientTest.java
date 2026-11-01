/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractApacheHttpClientTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected abstract InstrumentationExtension testing();

  protected abstract CloseableHttpClient createClient(boolean readTimeout);

  private CloseableHttpClient client;
  private CloseableHttpClient clientWithReadTimeout;

  @BeforeAll
  void setUp() {
    client = createClient(false);
    cleanup.deferAfterAll(client);
    clientWithReadTimeout = createClient(true);
    cleanup.deferAfterAll(clientWithReadTimeout);
  }

  CloseableHttpClient getClient(URI uri) {
    if (uri.getPath().endsWith("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

  static <T extends HttpRequest> T configureRequest(T request, Map<String, String> headers) {
    request.addHeader("user-agent", "apachehttpclient");
    headers.forEach((key, value) -> request.setHeader(new BasicHeader(key, value)));
    return request;
  }

  static int getResponseCode(HttpResponse response) {
    try {
      HttpEntity entity = response.getEntity();
      if (entity != null) {
        InputStream content = entity.getContent();
        if (content != null) {
          content.close();
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return response.getStatusLine().getStatusCode();
  }

  static ResponseHandler<HttpResponse> responseCallback(HttpClientResult httpClientResult) {
    return response -> {
      try {
        httpClientResult.complete(getResponseCode(response));
      } catch (Throwable t) {
        httpClientResult.complete(t);
        return response;
      }
      return response;
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

  abstract static class ApacheHttpClientTest<T> extends AbstractHttpClientTest<T> {
    @Override
    protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
      optionsBuilder.markAsLowLevelInstrumentation();
    }
  }

  @Nested
  class ApacheClientHostRequestTest extends ApacheHttpClientTest<BasicHttpRequest> {

    @Override
    protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
      super.configure(optionsBuilder);
      optionsBuilder.setTestProxy(true).setExpectProxyNetworkPeerAttributes(false);
    }

    @Test
    void requestWithInetAddress() throws IOException {
      int serverPort = resolveAddress("/success").getPort();
      HttpHost target =
          new HttpHost(
              InetAddress.getByAddress("127.0.0.1", new byte[] {127, 0, 0, 1}), serverPort, "http");
      URI expectedUri = URI.create(target.toURI() + "/success");

      int responseCode =
          getClient(expectedUri)
              .execute(
                  target,
                  new BasicHttpRequest("GET", "/success"),
                  AbstractApacheHttpClientTest::getResponseCode);
      assertThat(responseCode).isEqualTo(200);

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> assertClientSpanWithoutPeer(span, expectedUri),
                  span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
    }

    @Override
    public int sendRequestWithProxy(
        BasicHttpRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        InetSocketAddress proxyAddress)
        throws IOException {
      // Keep an explicit address so the regression exercises target-derived peer attributes.
      // Preserve the hostname to avoid reverse DNS when HttpCore 4.4 reads it.
      InetAddress targetAddress =
          InetAddress.getByAddress(
              uri.getHost(), InetAddress.getByName(uri.getHost()).getAddress());
      HttpHost target = new HttpHost(targetAddress, uri.getPort(), uri.getScheme());

      HttpClientContext context = HttpClientContext.create();
      context.setRequestConfig(
          RequestConfig.custom()
              .setConnectTimeout((int) connectTimeout().toMillis())
              .setSocketTimeout((int) readTimeout().toMillis())
              .setProxy(
                  new HttpHost(proxyAddress.getAddress().getHostAddress(), proxyAddress.getPort()))
              .build());

      return getClient(uri)
          .execute(target, request, AbstractApacheHttpClientTest::getResponseCode, context);
    }

    private SpanDataAssert assertClientSpanWithoutPeer(SpanDataAssert span, URI uri) {
      return assertClientSpan(span, uri, "GET", 200, null)
          .hasNoParent()
          .hasStatus(StatusData.unset())
          .hasAttributesSatisfyingExactly(
              equalTo(HTTP_REQUEST_METHOD, "GET"),
              equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
              equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
              equalTo(SERVER_ADDRESS, uri.getHost()),
              equalTo(SERVER_PORT, uri.getPort()),
              equalTo(URL_FULL, uri.toString()),
              equalTo(NETWORK_PEER_ADDRESS, null),
              equalTo(NETWORK_PEER_PORT, null));
    }

    @Override
    public BasicHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      // also testing with an absolute path below
      return configureRequest(new BasicHttpRequest(method, fullPathFromUri(uri)), headers);
    }

    @Override
    public int sendRequest(
        BasicHttpRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(
          getClient(uri)
              .execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request));
    }

    @Override
    public void sendRequestWithCallback(
        BasicHttpRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        HttpClientResult httpClientResult) {
      try {
        getClient(uri)
            .execute(
                new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
                request,
                responseCallback(httpClientResult));
      } catch (Throwable t) {
        httpClientResult.complete(t);
      }
    }
  }

  @Nested
  class ApacheClientHostRequestContextTest extends ApacheHttpClientTest<BasicHttpRequest> {

    @Override
    public BasicHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      // also testing with an absolute path below
      return configureRequest(new BasicHttpRequest(method, fullPathFromUri(uri)), headers);
    }

    @Override
    public int sendRequest(
        BasicHttpRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(
          getClient(uri)
              .execute(
                  new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
                  request,
                  new BasicHttpContext()));
    }

    @Override
    public void sendRequestWithCallback(
        BasicHttpRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        HttpClientResult httpClientResult) {
      try {
        getClient(uri)
            .execute(
                new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
                request,
                responseCallback(httpClientResult),
                new BasicHttpContext());
      } catch (Throwable t) {
        httpClientResult.complete(t);
      }
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestTest extends ApacheHttpClientTest<BasicHttpRequest> {

    @Override
    public BasicHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      return configureRequest(new BasicHttpRequest(method, uri.toString()), headers);
    }

    @Override
    public int sendRequest(
        BasicHttpRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(
          getClient(uri)
              .execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request));
    }

    @Override
    public void sendRequestWithCallback(
        BasicHttpRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        HttpClientResult httpClientResult) {
      try {
        getClient(uri)
            .execute(
                new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
                request,
                responseCallback(httpClientResult));
      } catch (Throwable t) {
        httpClientResult.complete(t);
      }
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestContextTest
      extends ApacheHttpClientTest<BasicHttpRequest> {

    @Override
    public BasicHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      return configureRequest(new BasicHttpRequest(method, uri.toString()), headers);
    }

    @Override
    public int sendRequest(
        BasicHttpRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(
          getClient(uri)
              .execute(
                  new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
                  request,
                  new BasicHttpContext()));
    }

    @Override
    public void sendRequestWithCallback(
        BasicHttpRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        HttpClientResult httpClientResult) {
      try {
        getClient(uri)
            .execute(
                new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
                request,
                responseCallback(httpClientResult),
                new BasicHttpContext());
      } catch (Throwable t) {
        httpClientResult.complete(t);
      }
    }
  }

  @Nested
  class ApacheClientUriRequestTest extends ApacheHttpClientTest<HttpUriRequest> {

    @Override
    public HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      // also testing with an absolute path below
      return configureRequest(new HttpUriRequest(method, uri), headers);
    }

    @Override
    public int sendRequest(
        HttpUriRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(getClient(uri).execute(request));
    }

    @Override
    public void sendRequestWithCallback(
        HttpUriRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        HttpClientResult httpClientResult) {
      try {
        getClient(uri).execute(request, responseCallback(httpClientResult));
      } catch (Throwable t) {
        httpClientResult.complete(t);
      }
    }
  }

  @Nested
  class ApacheClientUriRequestContextTest extends ApacheHttpClientTest<HttpUriRequest> {

    @Override
    public HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      // also testing with an absolute path below
      return configureRequest(new HttpUriRequest(method, uri), headers);
    }

    @Override
    public int sendRequest(
        HttpUriRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(getClient(uri).execute(request, new BasicHttpContext()));
    }

    @Override
    public void sendRequestWithCallback(
        HttpUriRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        HttpClientResult httpClientResult) {
      try {
        getClient(uri).execute(request, responseCallback(httpClientResult), new BasicHttpContext());
      } catch (Throwable t) {
        httpClientResult.complete(t);
      }
    }
  }
}
