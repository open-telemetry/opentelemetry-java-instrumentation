/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

  @Nested
  class ApacheClientHostRequestTest extends AbstractHttpClientTest<BasicHttpRequest> {

    @Override
    protected BasicHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      // also testing with an absolute path below
      return configureRequest(new BasicHttpRequest(method, fullPathFromUri(uri)), headers);
    }

    @Override
    protected int sendRequest(
        BasicHttpRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(
          getClient(uri)
              .execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request));
    }

    @Override
    protected void sendRequestWithCallback(
        BasicHttpRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        RequestResult requestResult) {
      try {
        getClient(uri)
            .execute(
                new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
                request,
                responseCallback(requestResult));
      } catch (Throwable t) {
        requestResult.complete(t);
      }
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      configureTest(options);
    }
  }

  @Nested
  class ApacheClientHostRequestContextTest extends AbstractHttpClientTest<BasicHttpRequest> {

    @Override
    protected BasicHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      // also testing with an absolute path below
      return configureRequest(new BasicHttpRequest(method, fullPathFromUri(uri)), headers);
    }

    @Override
    protected int sendRequest(
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
    protected void sendRequestWithCallback(
        BasicHttpRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        RequestResult requestResult) {
      try {
        getClient(uri)
            .execute(
                new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
                request,
                responseCallback(requestResult),
                new BasicHttpContext());
      } catch (Throwable t) {
        requestResult.complete(t);
      }
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      configureTest(options);
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestTest extends AbstractHttpClientTest<BasicHttpRequest> {

    @Override
    protected BasicHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      return configureRequest(new BasicHttpRequest(method, uri.toString()), headers);
    }

    @Override
    protected int sendRequest(
        BasicHttpRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(
          getClient(uri)
              .execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request));
    }

    @Override
    protected void sendRequestWithCallback(
        BasicHttpRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        RequestResult requestResult) {
      try {
        getClient(uri)
            .execute(
                new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
                request,
                responseCallback(requestResult));
      } catch (Throwable t) {
        requestResult.complete(t);
      }
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      configureTest(options);
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestContextTest
      extends AbstractHttpClientTest<BasicHttpRequest> {

    @Override
    protected BasicHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      return configureRequest(new BasicHttpRequest(method, uri.toString()), headers);
    }

    @Override
    protected int sendRequest(
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
    protected void sendRequestWithCallback(
        BasicHttpRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        RequestResult requestResult) {
      try {
        getClient(uri)
            .execute(
                new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
                request,
                responseCallback(requestResult),
                new BasicHttpContext());
      } catch (Throwable t) {
        requestResult.complete(t);
      }
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      configureTest(options);
    }
  }

  @Nested
  class ApacheClientUriRequestTest extends AbstractHttpClientTest<HttpUriRequest> {

    @Override
    protected HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      // also testing with an absolute path below
      return configureRequest(new HttpUriRequest(method, uri), headers);
    }

    @Override
    protected int sendRequest(
        HttpUriRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(getClient(uri).execute(request));
    }

    @Override
    protected void sendRequestWithCallback(
        HttpUriRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        RequestResult requestResult) {
      try {
        getClient(uri).execute(request, responseCallback(requestResult));
      } catch (Throwable t) {
        requestResult.complete(t);
      }
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      configureTest(options);
    }
  }

  @Nested
  class ApacheClientUriRequestContextTest extends AbstractHttpClientTest<HttpUriRequest> {

    @Override
    protected HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      // also testing with an absolute path below
      return configureRequest(new HttpUriRequest(method, uri), headers);
    }

    @Override
    protected int sendRequest(
        HttpUriRequest request, String method, URI uri, Map<String, String> headers)
        throws Exception {
      return getResponseCode(getClient(uri).execute(request, new BasicHttpContext()));
    }

    @Override
    protected void sendRequestWithCallback(
        HttpUriRequest request,
        String method,
        URI uri,
        Map<String, String> headers,
        RequestResult requestResult) {
      try {
        getClient(uri).execute(request, responseCallback(requestResult), new BasicHttpContext());
      } catch (Throwable t) {
        requestResult.complete(t);
      }
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      configureTest(options);
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

  static ResponseHandler<HttpResponse> responseCallback(
      AbstractHttpClientTest.RequestResult requestResult) {
    return response -> {
      try {
        requestResult.complete(getResponseCode(response));
      } catch (Throwable t) {
        requestResult.complete(t);
        return response;
      }
      return response;
    };
  }

  static void configureTest(HttpClientTestOptions options) {
    options.setUserAgent("apachehttpclient");
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
