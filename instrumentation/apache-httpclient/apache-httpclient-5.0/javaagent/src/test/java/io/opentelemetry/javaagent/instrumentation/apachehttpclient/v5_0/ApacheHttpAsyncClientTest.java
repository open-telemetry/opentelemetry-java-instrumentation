/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.api.impl.InstrumentationUtil.suppressInstrumentation;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
    @Test
    void suppressedRequestPreservesCallbackContextWithoutCallerSpan() throws Throwable {
      ContextKey<String> key = ContextKey.named("callback-context");
      AtomicReference<String> callbackValue = new AtomicReference<>();
      HttpClientResult result =
          new HttpClientResult(() -> callbackValue.set(Context.current().get(key)));
      SimpleHttpRequest request = new SimpleHttpRequest("GET", resolveAddress("/success"));

      try (Scope ignored = Context.root().with(key, "caller").makeCurrent()) {
        suppressInstrumentation(
            () -> client.execute(request, getContext(), new ResponseCallback(result)));
      }

      assertThat(result.get()).isEqualTo(200);
      assertThat(callbackValue.get()).isEqualTo("caller");
      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> assertServerSpan(span).hasNoParent()));
    }

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

  @Nested
  class ApacheClientNullContextTest extends AbstractTest {
    @Override
    SimpleHttpRequest createRequest(String method, URI uri) {
      return new SimpleHttpRequest(method, uri);
    }

    @Override
    protected HttpContext getContext() {
      return null;
    }
  }

  abstract class AbstractTest extends AbstractApacheHttpClientTest<SimpleHttpRequest> {

    @Override
    protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
      super.configure(optionsBuilder);
      optionsBuilder.spanEndsAfterBody();
    }

    @Override
    public SimpleHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
      SimpleHttpRequest httpRequest = super.buildRequest(method, uri, headers);
      RequestConfig.Builder configBuilder = RequestConfig.custom();
      configBuilder.setConnectTimeout(getTimeout(connectTimeout()));
      if (uri.getPath().endsWith("/read-timeout")) {
        configBuilder.setResponseTimeout(getTimeout(readTimeout()));
      }
      RequestConfig requestConfig = configBuilder.build();
      httpRequest.setConfig(requestConfig);
      return httpRequest;
    }

    @Override
    HttpResponse executeRequest(SimpleHttpRequest request, URI uri) throws Exception {
      return client.execute(request, getContext(), null).get(30, SECONDS);
    }

    @Override
    void executeRequestWithCallback(SimpleHttpRequest request, URI uri, HttpClientResult result) {
      client.execute(request, getContext(), new ResponseCallback(result));
    }
  }

  private static class ResponseCallback implements FutureCallback<SimpleHttpResponse> {
    private final HttpClientResult httpClientResult;

    ResponseCallback(HttpClientResult httpClientResult) {
      this.httpClientResult = httpClientResult;
    }

    @Override
    public void completed(SimpleHttpResponse response) {
      httpClientResult.complete(response.getCode());
    }

    @Override
    public void failed(Exception ex) {
      httpClientResult.complete(ex);
    }

    @Override
    public void cancelled() {
      httpClientResult.complete(new CancellationException());
    }
  }
}
