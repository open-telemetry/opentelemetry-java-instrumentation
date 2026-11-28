/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.api.impl.InstrumentationUtil.suppressInstrumentation;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationBeforeSpanPublication(boolean cancelDuringStart) throws Throwable {
      ContextKey<String> key = ContextKey.named("cancellation-context");
      AtomicReference<String> callbackValue = new AtomicReference<>();
      AtomicInteger callbacks = new AtomicInteger();
      AtomicInteger injections = new AtomicInteger();
      AtomicReference<Runnable> onInjection = new AtomicReference<>(() -> {});
      HttpRequest request =
          new BasicHttpRequest("GET", resolveAddress("/success")) {
            @Override
            public void setHeader(String name, Object value) {
              super.setHeader(name, value);
              if (name.equals("traceparent")) {
                // start() injects before publishing its context. Observe this as well as exports:
                // checking only ended spans would miss a start left unmatched by cancellation.
                injections.incrementAndGet();
                onInjection.get().run();
              }
            }
          };
      DeferredRequestProducer producer = new DeferredRequestProducer(request);
      HttpClientResult result =
          new HttpClientResult(() -> {
            callbacks.incrementAndGet();
            callbackValue.set(Context.current().get(key));
            testing.runWithSpan("callback", () -> {});
          });

      try (CloseableHttpAsyncClient deferredClient =
          HttpAsyncClients.custom()
              .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
              .addExecInterceptorBefore(
                  ChainElement.REDIRECT.name(),
                  "cancelled-request",
                  (req, entity, scope, chain, callback) ->
                      callback.failed(new CancellationException()))
              .build()) {
        deferredClient.start();
        testing.runWithSpan(
            "parent",
            () -> {
              Future<SimpleHttpResponse> future;
              try (Scope ignored = Context.current().with(key, "caller").makeCurrent()) {
                future =
                    deferredClient.execute(
                        producer,
                        SimpleResponseConsumer.create(),
                        null,
                        getContext(),
                        new ResponseCallback(result));
              }
              Runnable cancel =
                  () -> {
                    CompletableFuture<Boolean> cancellation =
                        CompletableFuture.supplyAsync(() -> {
                          try (Scope ignored =
                              Context.root().with(key, "canceller").makeCurrent()) {
                            boolean cancelled = future.cancel(true);
                            assertThat(Context.current().get(key)).isEqualTo("canceller");
                            return cancelled;
                          }
                        });
                    assertThat(cancellation).succeedsWithin(Duration.ofSeconds(10)).isEqualTo(true);
                  };
              if (cancelDuringStart) {
                onInjection.set(cancel);
              } else {
                cancel.run();
              }
              producer.send();
              assertThat(future.isCancelled()).isTrue();
              assertThatThrownBy(result::get).isInstanceOf(CancellationException.class);
            });
      }

      assertThat(callbacks.get()).isEqualTo(1);
      assertThat(callbackValue.get()).isEqualTo("caller");
      assertThat(injections.get()).isEqualTo(cancelDuringStart ? 1 : 0);
      testing.waitAndAssertTraces(trace -> {
        if (cancelDuringStart) {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasNoParent(),
              span ->
                  span.hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasStatus(StatusData.unset()),
              span -> span.hasName("callback").hasParent(trace.getSpan(0)));
        } else {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasNoParent(),
              span -> span.hasName("callback").hasParent(trace.getSpan(0)));
        }
      });
    }

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

  private static class DeferredRequestProducer extends BasicRequestProducer {
    private RequestChannel channel;
    private HttpContext context;

    DeferredRequestProducer(HttpRequest request) {
      super(request, null);
    }

    @Override
    public void sendRequest(RequestChannel channel, HttpContext context) {
      this.channel = channel;
      this.context = context;
    }

    void send() throws HttpException, IOException {
      assertThat(channel).isNotNull();
      super.sendRequest(channel, context);
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
