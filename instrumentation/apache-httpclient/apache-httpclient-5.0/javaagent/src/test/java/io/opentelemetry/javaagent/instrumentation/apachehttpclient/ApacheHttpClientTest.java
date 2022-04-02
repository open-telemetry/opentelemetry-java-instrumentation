/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApacheHttpClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  // TODO(anuraaga): AbstractHttpClientTest should provide timeout values statically
  private final RequestConfig requestConfig =
      RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(5)).build();
  private final RequestConfig requestWithReadTimeoutConfig =
      RequestConfig.copy(requestConfig).setResponseTimeout(Timeout.ofSeconds(2)).build();

  private final CloseableHttpClient client =
      HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
  private final CloseableHttpClient clientWithReadTimeout =
      HttpClients.custom().setDefaultRequestConfig(requestWithReadTimeoutConfig).build();

  @AfterAll
  void tearDown() {
    client.close(CloseMode.GRACEFUL);
    clientWithReadTimeout.close(CloseMode.GRACEFUL);
  }

  private CloseableHttpClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

  private static String fullPathFromURI(URI uri) {
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

  private static class ResponseHandler implements HttpClientResponseHandler<Void> {
    private final AbstractHttpClientTest.RequestResult requestResult;

    public ResponseHandler(AbstractHttpClientTest.RequestResult requestResult) {
      this.requestResult = requestResult;
    }

    @Override
    public Void handleResponse(ClassicHttpResponse response) throws IOException {
      response.close();
      requestResult.complete(response.getCode());
      return null;
    }
  }

  abstract static class AbstractTest<T extends HttpRequest> extends AbstractHttpClientTest<T> {
    @Override
    protected String userAgent() {
      return "apachehttpclient";
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      options.setUserAgent(userAgent());
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

    @Override
    protected T buildRequest(String method, URI uri, Map<String, String> headers) {
      T request = createRequest(method, uri);
      request.addHeader("user-agent", userAgent());
      headers.forEach((key, value) -> request.setHeader(new BasicHeader(key, value)));
      return request;
    }

    @Override
    protected int sendRequest(T request, String method, URI uri, Map<String, String> headers)
        throws IOException {
      ClassicHttpResponse response = executeRequest(request, uri);
      response.close(); // Make sure the connection is closed.
      return response.getCode();
    }

    @Override
    protected void sendRequestWithCallback(
        T request,
        String method,
        URI uri,
        Map<String, String> headers,
        AbstractHttpClientTest.RequestResult requestResult) {
      try {
        executeRequestWithCallback(request, uri, new ResponseHandler(requestResult));
      } catch (Throwable throwable) {
        requestResult.complete(throwable);
      }
    }

    protected HttpHost getHost(URI uri) {
      return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    }

    abstract T createRequest(String method, URI uri);

    abstract ClassicHttpResponse executeRequest(T request, URI uri) throws IOException;

    abstract void executeRequestWithCallback(T request, URI uri, ResponseHandler responseHandler)
        throws IOException;
  }

  @Nested
  class ApacheClientHostRequestTest extends AbstractTest<ClassicHttpRequest> {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      // also testing with an absolute path below
      return new BasicClassicHttpRequest(method, fullPathFromURI(uri));
    }

    @Override
    ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws IOException {
      return getClient(uri).execute(getHost(uri), request);
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, ResponseHandler handler)
        throws IOException {
      getClient(uri).execute(getHost(uri), request, handler);
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestTest extends AbstractTest<ClassicHttpRequest> {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      return new BasicClassicHttpRequest(method, uri.toString());
    }

    @Override
    ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws IOException {
      return getClient(uri).execute(getHost(uri), request);
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, ResponseHandler handler)
        throws IOException {
      getClient(uri).execute(getHost(uri), request, handler);
    }
  }

  @Nested
  class ApacheClientHostRequestContextTest extends AbstractTest<ClassicHttpRequest> {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      // also testing with an absolute path below
      return new BasicClassicHttpRequest(method, fullPathFromURI(uri));
    }

    @Override
    ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws IOException {
      return getClient(uri).execute(getHost(uri), request, new BasicHttpContext());
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, ResponseHandler handler)
        throws IOException {
      getClient(uri).execute(getHost(uri), request, new BasicHttpContext(), handler);
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestContextTest extends AbstractTest<ClassicHttpRequest> {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      return new BasicClassicHttpRequest(method, uri.toString());
    }

    @Override
    ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws IOException {
      return getClient(uri).execute(getHost(uri), request, new BasicHttpContext());
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, ResponseHandler handler)
        throws IOException {
      getClient(uri).execute(getHost(uri), request, new BasicHttpContext(), handler);
    }
  }

  @Nested
  class ApacheClientUriRequestTest extends AbstractTest<ClassicHttpRequest> {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      return new HttpUriRequestBase(method, uri);
    }

    @Override
    ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws IOException {
      return getClient(uri).execute(request);
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, ResponseHandler handler)
        throws IOException {
      getClient(uri).execute(request, handler);
    }
  }

  @Nested
  class ApacheClientUriRequestContextTest extends AbstractTest<ClassicHttpRequest> {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      return new HttpUriRequestBase(method, uri);
    }

    @Override
    ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws IOException {
      return getClient(uri).execute(request, new BasicHttpContext());
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, ResponseHandler handler)
        throws IOException {
      getClient(uri).execute(request, new BasicHttpContext(), handler);
    }
  }
}
