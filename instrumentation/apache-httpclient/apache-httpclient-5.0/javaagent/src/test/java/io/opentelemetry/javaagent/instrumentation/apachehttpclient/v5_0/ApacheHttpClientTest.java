/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.CONNECTION_TIMEOUT;
import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.READ_TIMEOUT;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.io.IOException;
import java.net.URI;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ApacheHttpClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private final CloseableHttpClient client = createClient();
  private final CloseableHttpClient clientWithReadTimeout = createClientWithReadTimeout();

  private static RequestConfig requestConfig() {
    return RequestConfig.custom()
        .setConnectTimeout(AbstractApacheHttpClientTest.getTimeout(CONNECTION_TIMEOUT))
        .build();
  }

  private static RequestConfig requestConfigWithReadTimeout() {
    return RequestConfig.copy(requestConfig())
        .setResponseTimeout(AbstractApacheHttpClientTest.getTimeout(READ_TIMEOUT))
        .build();
  }

  private static CloseableHttpClient createClient() {
    return HttpClients.custom().setDefaultRequestConfig(requestConfig()).build();
  }

  private static CloseableHttpClient createClientWithReadTimeout() {
    return HttpClients.custom().setDefaultRequestConfig(requestConfigWithReadTimeout()).build();
  }

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

  @Nested
  class ApacheClientHostRequestTest extends AbstractTest {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      // also testing with an absolute path below
      return new BasicClassicHttpRequest(method, fullPathFromUri(uri));
    }

    @Override
    ClassicHttpResponse doExecuteRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(getHost(uri), request);
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, HttpClientResult result)
        throws Exception {
      getClient(uri).execute(getHost(uri), request, new ResponseHandler(result));
    }
  }

  @Nested
  class ApacheClientNullHttpHostRequestTest extends AbstractTest {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      // also testing with an absolute path below
      return new BasicClassicHttpRequest(method, HttpHost.create(uri), fullPathFromUri(uri));
    }

    @Override
    ClassicHttpResponse doExecuteRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(null, request);
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, HttpClientResult result)
        throws Exception {
      getClient(uri).execute(null, request, new ResponseHandler(result));
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestTest extends AbstractTest {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      return new BasicClassicHttpRequest(method, uri.toString());
    }

    @Override
    ClassicHttpResponse doExecuteRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(getHost(uri), request);
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, HttpClientResult result)
        throws Exception {
      getClient(uri).execute(getHost(uri), request, new ResponseHandler(result));
    }
  }

  @Nested
  class ApacheClientHostRequestContextTest extends AbstractTest {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      // also testing with an absolute path below
      return new BasicClassicHttpRequest(method, fullPathFromUri(uri));
    }

    @Override
    ClassicHttpResponse doExecuteRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(getHost(uri), request, getContext());
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, HttpClientResult result)
        throws Exception {
      getClient(uri).execute(getHost(uri), request, getContext(), new ResponseHandler(result));
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestContextTest extends AbstractTest {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      return new BasicClassicHttpRequest(method, uri.toString());
    }

    @Override
    ClassicHttpResponse doExecuteRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(getHost(uri), request, getContext());
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, HttpClientResult result)
        throws Exception {
      getClient(uri).execute(getHost(uri), request, getContext(), new ResponseHandler(result));
    }
  }

  @Nested
  class ApacheClientUriRequestTest extends AbstractTest {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      return new HttpUriRequestBase(method, uri);
    }

    @Override
    ClassicHttpResponse doExecuteRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(request);
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, HttpClientResult result)
        throws Exception {
      getClient(uri).execute(request, new ResponseHandler(result));
    }
  }

  @Nested
  class ApacheClientUriRequestContextTest extends AbstractTest {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      return new HttpUriRequestBase(method, uri);
    }

    @Override
    ClassicHttpResponse doExecuteRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(request, getContext());
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, HttpClientResult result)
        throws Exception {
      getClient(uri).execute(request, getContext(), new ResponseHandler(result));
    }
  }

  abstract static class AbstractTest extends AbstractApacheHttpClientTest<ClassicHttpRequest> {
    @Override
    final HttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws Exception {
      ClassicHttpResponse httpResponse = doExecuteRequest(request, uri);
      httpResponse.close();
      return httpResponse;
    }

    abstract ClassicHttpResponse doExecuteRequest(ClassicHttpRequest request, URI uri)
        throws Exception;

    @Override
    protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
      super.configure(optionsBuilder);
      // apparently apache http client does not report the 302 status code?
      optionsBuilder.setResponseCodeOnRedirectError(null);

      if (Boolean.getBoolean("testLatestDeps")) {
        optionsBuilder.disableTestHttps();
        optionsBuilder.disableTestRemoteConnection();
      }
    }
  }

  private static class ResponseHandler implements HttpClientResponseHandler<Void> {
    private final HttpClientResult httpClientResult;

    public ResponseHandler(HttpClientResult httpClientResult) {
      this.httpClientResult = httpClientResult;
    }

    @Override
    public Void handleResponse(ClassicHttpResponse response) throws IOException {
      response.close();
      httpClientResult.complete(response.getCode());
      return null;
    }
  }
}
