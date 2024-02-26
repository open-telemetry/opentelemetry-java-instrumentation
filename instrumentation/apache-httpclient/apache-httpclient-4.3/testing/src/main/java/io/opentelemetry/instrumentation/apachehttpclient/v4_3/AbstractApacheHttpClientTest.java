/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Map;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractApacheHttpClientTest {

  protected abstract InstrumentationExtension testing();

  protected abstract CloseableHttpClient createClient(boolean readTimeout);

  private CloseableHttpClient client;
  private CloseableHttpClient clientWithReadTimeout;

  @BeforeAll
  void setUp() {
    client = createClient(false);
    clientWithReadTimeout = createClient(true);
  }

  @AfterAll
  void tearDown() throws Exception {
    client.close();
    clientWithReadTimeout.close();
  }

  CloseableHttpClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
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

  static <T extends HttpRequest> T configureRequest(T request, Map<String, String> headers) {
    request.addHeader("user-agent", "apachehttpclient");
    headers.forEach((key, value) -> request.setHeader(new BasicHeader(key, value)));
    return request;
  }

  static int getResponseCode(HttpResponse response) {
    try {
      if (response.getEntity() != null && response.getEntity().getContent() != null) {
        response.getEntity().getContent().close();
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
}
