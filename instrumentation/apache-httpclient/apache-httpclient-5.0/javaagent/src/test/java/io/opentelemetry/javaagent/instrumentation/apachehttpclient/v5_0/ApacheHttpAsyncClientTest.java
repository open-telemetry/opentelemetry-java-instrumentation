/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApacheHttpAsyncClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private final CloseableHttpAsyncClient client = createClient();

  @BeforeAll
  void setUp() {
    client.start();
  }

  @AfterAll
  void tearDown() {
    client.close(CloseMode.GRACEFUL);
  }

  private static CloseableHttpAsyncClient createClient() {
    return HttpAsyncClients.custom().setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1).build();
  }

  @Nested
  class ApacheClientUriRequestTest extends AbstractTest {
    @Override
    SimpleHttpRequest createRequest(String method, URI uri) {
      return new SimpleHttpRequest(method, uri);
    }
  }

  @Nested
  class ApacheClientHostRequestTest extends AbstractTest {
    @Override
    SimpleHttpRequest createRequest(String method, URI uri) {
      return new SimpleHttpRequest(method, getHost(uri), fullPathFromUri(uri));
    }
  }

  @Nested
  class ApacheClientHostAbsoluteUriRequestTest extends AbstractTest {
    @Override
    SimpleHttpRequest createRequest(String method, URI uri) {
      return new SimpleHttpRequest(method, URI.create(uri.toString()));
    }
  }

  abstract class AbstractTest extends AbstractApacheHttpClientTest<SimpleHttpRequest> {
    @Override
    protected SimpleHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      SimpleHttpRequest httpRequest = super.buildRequest(method, uri, headers);
      RequestConfig.Builder configBuilder = RequestConfig.custom();
      configBuilder.setConnectTimeout(getTimeout(connectTimeout()));
      if (uri.toString().contains("/read-timeout")) {
        configBuilder.setResponseTimeout(getTimeout(readTimeout()));
      }
      RequestConfig requestConfig = configBuilder.build();
      httpRequest.setConfig(requestConfig);
      return httpRequest;
    }

    @Override
    HttpResponse executeRequest(SimpleHttpRequest request, URI uri) throws Exception {
      return client.execute(request, null).get();
    }

    @Override
    void executeRequestWithCallback(SimpleHttpRequest request, URI uri, RequestResult result) {
      client.execute(request, new ResponseCallback(result));
    }

    @Override
    protected void configure(HttpClientTestOptions options) {
      super.configure(options);
      options.setResponseCodeOnRedirectError(302);
    }
  }

  private static class ResponseCallback implements FutureCallback<SimpleHttpResponse> {
    private final AbstractHttpClientTest.RequestResult requestResult;

    public ResponseCallback(AbstractHttpClientTest.RequestResult requestResult) {
      this.requestResult = requestResult;
    }

    @Override
    public void completed(SimpleHttpResponse response) {
      requestResult.complete(response.getCode());
    }

    @Override
    public void failed(Exception ex) {
      requestResult.complete(ex);
    }

    @Override
    public void cancelled() {
      requestResult.complete(new CancellationException());
    }
  }
}
