/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import java.io.IOException;
import java.net.URI;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ApacheHttpClientTest {
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

  @Nested
  class ApacheClientHostRequestTest extends AbstractTest {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      // also testing with an absolute path below
      return new BasicClassicHttpRequest(method, fullPathFromURI(uri));
    }

    @Override
    ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(getHost(uri), request);
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, RequestResult result)
        throws Exception {
      getClient(uri).execute(getHost(uri), request, new ResponseHandler(result));
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestTest extends AbstractTest {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      return new BasicClassicHttpRequest(method, uri.toString());
    }

    @Override
    ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(getHost(uri), request);
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, RequestResult result)
        throws Exception {
      getClient(uri).execute(getHost(uri), request, new ResponseHandler(result));
    }
  }

  @Nested
  class ApacheClientHostRequestContextTest extends AbstractTest {
    @Override
    ClassicHttpRequest createRequest(String method, URI uri) {
      // also testing with an absolute path below
      return new BasicClassicHttpRequest(method, fullPathFromURI(uri));
    }

    @Override
    ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(getHost(uri), request, getContext());
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, RequestResult result)
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
    ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(getHost(uri), request, getContext());
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, RequestResult result)
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
    ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(request);
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, RequestResult result)
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
    ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) throws Exception {
      return getClient(uri).execute(request, getContext());
    }

    @Override
    void executeRequestWithCallback(ClassicHttpRequest request, URI uri, RequestResult result)
        throws Exception {
      getClient(uri).execute(request, getContext(), new ResponseHandler(result));
    }
  }

  abstract static class AbstractTest extends AbstractApacheHttpClientTest<ClassicHttpRequest> {}

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
}
