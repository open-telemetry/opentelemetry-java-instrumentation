/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class AbstractJettyClient12Test extends AbstractHttpClientTest<Request> {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private HttpClient client;
  private HttpClient httpsClient;

  protected abstract HttpClient createStandardClient();

  protected abstract HttpClient createHttpsClient(SslContextFactory.Client sslContextFactory);

  @BeforeEach
  void before() throws Exception {
    client = createStandardClient();
    client.setConnectTimeout(CONNECTION_TIMEOUT.toMillis());
    client.start();
    cleanup.deferCleanup(client::stop);

    SslContextFactory.Client tlsCtx = new SslContextFactory.Client();
    httpsClient = createHttpsClient(tlsCtx);
    httpsClient.setFollowRedirects(false);
    httpsClient.start();
    cleanup.deferCleanup(httpsClient::stop);
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    // disable redirect tests
    optionsBuilder.disableTestRedirects();
    // Jetty 12 does not support reusing requests.
    // Calling request.send() twice blocks indefinitely.
    optionsBuilder.disableTestReusedRequest();
    optionsBuilder.spanEndsAfterBody();
  }

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpClient theClient = "https".equalsIgnoreCase(uri.getScheme()) ? httpsClient : client;

    Request request = theClient.newRequest(uri);
    request.agent("Jetty");

    request.method(method);
    if (uri.getPath().endsWith("/read-timeout")) {
      request.timeout(READ_TIMEOUT.toMillis(), MILLISECONDS);
    }

    return request;
  }

  @Override
  public int sendRequest(Request request, String method, URI uri, Map<String, String> headers)
      throws ExecutionException, InterruptedException, TimeoutException {
    headers.forEach((k, v) -> request.headers(httpFields -> httpFields.put(new HttpField(k, v))));

    ContentResponse response = request.send();

    return response.getStatus();
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult) {
    JettyClientListener clientListener = new JettyClientListener();

    request.onRequestFailure(clientListener);
    request.onResponseFailure(clientListener);
    headers.forEach((k, v) -> request.headers(httpFields -> httpFields.put(new HttpField(k, v))));

    request.send(
        result -> {
          if (clientListener.failure != null) {
            requestResult.complete(clientListener.failure);
            return;
          }

          requestResult.complete(result.getResponse().getStatus());
        });
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void deferredResponseContentRetainsCallerContext(boolean asyncListener, boolean withParent)
      throws ExecutionException, InterruptedException, TimeoutException {
    ContextKey<String> key = ContextKey.named("jetty-test");
    CompletableFuture<Context> lateContentContext = new CompletableFuture<>();
    AtomicReference<Context> contextAfterDemand = new AtomicReference<>();
    CompletableFuture<Result> completed = new CompletableFuture<>();
    Request request = client.newRequest(resolveAddress("/long-request")).method("GET");
    request.headers(headers -> headers.put(new HttpField("delay", "500")));
    Consumer<ByteBuffer> processContent =
        content -> {
          String chunk = UTF_8.decode(content.duplicate()).toString();
          if (chunk.contains("World")) {
            Context callbackContext = Context.current();
            testing.runWithSpan("response-body", () -> {});
            lateContentContext.complete(callbackContext);
          }
        };
    if (asyncListener) {
      request.onResponseContentAsync(
          new Response.AsyncContentListener() {
            @Override
            public void onContent(Response response, Content.Chunk chunk, Runnable demander) {
              processContent.accept(chunk.getByteBuffer());
              Context deliveryContext = Context.current().with(key, "delivery");
              try (Scope ignored = deliveryContext.makeCurrent()) {
                demander.run();
                contextAfterDemand.set(Context.current());
              }
            }
          });
    } else {
      request.onResponseContent(
          new Response.ContentListener() {
            @Override
            public void onContent(Response response, ByteBuffer content) {
              processContent.accept(content);
            }
          });
    }

    Runnable send =
        () -> {
          Context caller =
              Baggage.builder()
                  .put("jetty-test", "caller")
                  .build()
                  .storeInContext(Context.current().with(key, "caller"));
          try (Scope ignored = caller.makeCurrent()) {
            request.send(completed::complete);
          }
        };
    if (withParent) {
      testing.runWithSpan("parent", send::run);
    } else {
      send.run();
    }

    Result result = completed.get(10, SECONDS);
    assertThat(result.isSucceeded()).isTrue();
    Context observedContext = lateContentContext.get(10, SECONDS);
    assertThat(observedContext.get(key)).isEqualTo("caller");
    assertThat(Baggage.fromContext(observedContext).getEntryValue("jetty-test"))
        .isEqualTo("caller");
    if (asyncListener) {
      assertThat(contextAfterDemand.get().get(key)).isEqualTo("delivery");
    }

    if (withParent) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasNoParent(),
                  span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                  span ->
                      span.hasName("test-http-server")
                          .hasKind(SpanKind.SERVER)
                          .hasParent(trace.getSpan(1)),
                  span -> span.hasName("response-body").hasParent(trace.getSpan(0))));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasNoParent(),
                  span ->
                      span.hasName("test-http-server")
                          .hasKind(SpanKind.SERVER)
                          .hasParent(trace.getSpan(0))),
          trace ->
              trace.hasSpansSatisfyingExactly(span -> span.hasName("response-body").hasNoParent()));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pendingContentDemandFailureRetainsCallerContext(boolean withParent)
      throws ExecutionException, InterruptedException, TimeoutException {
    ContextKey<String> key = ContextKey.named("jetty-test");
    CompletableFuture<Content.Source> pendingSource = new CompletableFuture<>();
    CompletableFuture<Content.Source> idleSource = new CompletableFuture<>();
    CompletableFuture<Void> demandCalled = new CompletableFuture<>();
    CompletableFuture<Result> completed = new CompletableFuture<>();
    IOException failure = new IOException("content failed");
    Request request = client.newRequest(resolveAddress("/success"));
    request.onResponseContentSource(
        (response, source) -> {
          assertThat(source.read()).isNull();
          source.demand(
              () -> {
                Content.Chunk chunk = source.read();
                try {
                  assertThat(chunk.getFailure()).isSameAs(failure);
                  testing.runWithSpan("response-body", () -> {});
                  demandCalled.complete(null);
                } finally {
                  chunk.release();
                }
              });
          pendingSource.complete(source);
        });
    // Withhold this listener's demand so no content can satisfy the other listener's demand.
    request.onResponseContentSource((response, source) -> idleSource.complete(source));

    Runnable send = () -> request.send(completed::complete);
    if (withParent) {
      testing.runWithSpan("parent", send::run);
    } else {
      send.run();
    }

    Content.Source pending = pendingSource.get(10, SECONDS);
    Content.Source idle = idleSource.get(10, SECONDS);
    assertThat(demandCalled).isNotDone();
    Context unrelatedContext = Context.root().with(key, "unrelated");
    try (Scope ignored = unrelatedContext.makeCurrent()) {
      pending.fail(failure);
      assertThat(demandCalled).isCompleted();
      assertThat(Context.current().get(key)).isEqualTo(unrelatedContext.get(key));
    }
    try (Scope ignored = unrelatedContext.makeCurrent()) {
      idle.fail(failure);
      assertThat(Context.current().get(key)).isEqualTo(unrelatedContext.get(key));
    }

    assertThat(completed.get(10, SECONDS).getFailure()).isSameAs(failure);

    if (withParent) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactlyInAnyOrder(
                  span -> span.hasName("parent").hasNoParent(),
                  span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                  span -> span.hasName("test-http-server").hasParent(trace.getSpan(1)),
                  span -> span.hasName("response-body").hasParent(trace.getSpan(0))));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasNoParent(),
                  span -> span.hasName("test-http-server").hasParent(trace.getSpan(0))),
          trace ->
              trace.hasSpansSatisfyingExactly(span -> span.hasName("response-body").hasNoParent()));
    }
  }

  @ParameterizedTest
  @CsvSource({
    "true, success, false",
    "false, success, true",
    "true, asyncFailure, true",
    "false, asyncFailure, false",
    "true, inlineFailure, true",
    "false, inlineFailure, true"
  })
  void responseLifecycleContexts(boolean withParent, String outcome, boolean throwingListeners)
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<Result> completed = new CompletableFuture<>();
    IOException failure = new IOException("request failed");
    Request request = client.newRequest(resolveAddress("/success")).method("POST");
    if (outcome.equals("inlineFailure")) {
      request.onRequestQueued(queued -> queued.abort(failure));
    } else if (outcome.equals("asyncFailure")) {
      request.onRequestBegin(begun -> begun.abort(failure));
    }
    class LifecycleListener
        implements Response.HeadersListener,
            Response.SuccessListener,
            Response.FailureListener,
            Response.CompleteListener {
      private void record(String name) {
        testing.runWithSpan(name, () -> {});
        if (throwingListeners) {
          throw new IllegalStateException("response listener");
        }
      }

      @Override
      public void onHeaders(Response response) {
        record("listener-headers");
      }

      @Override
      public void onSuccess(Response response) {
        record("listener-success");
      }

      @Override
      public void onFailure(Response response, Throwable cause) {
        record("listener-failure");
      }

      @Override
      public void onComplete(Result result) {
        try {
          record("listener-complete");
        } finally {
          completed.complete(result);
        }
      }
    }

    LifecycleListener listener = new LifecycleListener();
    request.onResponseHeaders(response -> testing.runWithSpan("registered-headers", () -> {}));
    request.onResponseSuccess(response -> testing.runWithSpan("registered-success", () -> {}));
    request.onResponseFailure(
        (response, cause) -> testing.runWithSpan("registered-failure", () -> {}));
    request.onComplete(result -> testing.runWithSpan("registered-complete", () -> {}));

    Runnable send =
        () -> {
          request.send(listener);
          if (outcome.equals("inlineFailure")) {
            assertThat(completed).isCompleted();
          }
        };
    if (withParent) {
      testing.runWithSpan("parent", send::run);
    } else {
      send.run();
    }
    Result result = completed.get(10, SECONDS);
    assertThat(result.isSucceeded()).isEqualTo(outcome.equals("success"));
    if (outcome.equals("success")) {
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }
    if (!outcome.equals("success")) {
      assertThat(result.getFailure()).isSameAs(failure);
    }
    if (withParent && outcome.equals("success")) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactlyInAnyOrder(
                  span -> span.hasName("parent").hasNoParent(),
                  span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                  span -> span.hasName("test-http-server").hasParent(trace.getSpan(1)),
                  span -> span.hasName("listener-headers").hasParent(trace.getSpan(0)),
                  span -> span.hasName("listener-success").hasParent(trace.getSpan(0)),
                  span -> span.hasName("listener-complete").hasParent(trace.getSpan(0)),
                  span -> span.hasName("registered-headers").hasParent(trace.getSpan(0)),
                  span -> span.hasName("registered-success").hasParent(trace.getSpan(0)),
                  span -> span.hasName("registered-complete").hasParent(trace.getSpan(0))));
    } else if (withParent) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactlyInAnyOrder(
                  span -> span.hasName("parent").hasNoParent(),
                  span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                  span -> span.hasName("listener-failure").hasParent(trace.getSpan(0)),
                  span -> span.hasName("listener-complete").hasParent(trace.getSpan(0)),
                  span -> span.hasName("registered-failure").hasParent(trace.getSpan(0)),
                  span -> span.hasName("registered-complete").hasParent(trace.getSpan(0))));
    } else if (outcome.equals("success")) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasNoParent(),
                  span -> span.hasName("test-http-server").hasParent(trace.getSpan(0))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("registered-headers").hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("listener-headers").hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("registered-success").hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("listener-success").hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("registered-complete").hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("listener-complete").hasNoParent()));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("registered-failure").hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("listener-failure").hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("registered-complete").hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("listener-complete").hasNoParent()));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aggregateResponseListenerContext(boolean withParent)
      throws ReflectiveOperationException,
          ExecutionException,
          InterruptedException,
          TimeoutException {
    assumeTrue(testLatestDeps());

    CompletableFuture<Result> completed = new CompletableFuture<>();
    CompletableFuture<Result> aggregateCompleted = new CompletableFuture<>();
    Request request = client.newRequest(resolveAddress("/success")).method("POST");
    Request.class
        .getMethod("onResponseListener", Response.Listener.class)
        .invoke(
            request,
            new Response.Listener() {
              @Override
              public void onComplete(Result result) {
                try {
                  testing.runWithSpan("aggregate-complete", () -> {});
                } finally {
                  aggregateCompleted.complete(result);
                }
              }
            });

    Runnable send = () -> request.send(completed::complete);
    if (withParent) {
      testing.runWithSpan("parent", send::run);
    } else {
      send.run();
    }
    Result result = completed.get(10, SECONDS);
    assertThat(aggregateCompleted.get(10, SECONDS)).isSameAs(result);
    assertThat(result.isSucceeded()).isTrue();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    if (withParent) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactlyInAnyOrder(
                  span -> span.hasName("parent").hasNoParent(),
                  span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                  span -> span.hasName("test-http-server").hasParent(trace.getSpan(1)),
                  span -> span.hasName("aggregate-complete").hasParent(trace.getSpan(0))));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasNoParent(),
                  span -> span.hasName("test-http-server").hasParent(trace.getSpan(0))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("aggregate-complete").hasNoParent()));
    }
  }

  private static class JettyClientListener
      implements Request.FailureListener, Response.FailureListener {
    private volatile Throwable failure;

    @Override
    public void onFailure(Request request, Throwable failure) {
      this.failure = failure;
    }

    @Override
    public void onFailure(Response response, Throwable failure) {
      this.failure = failure;
    }
  }
}
