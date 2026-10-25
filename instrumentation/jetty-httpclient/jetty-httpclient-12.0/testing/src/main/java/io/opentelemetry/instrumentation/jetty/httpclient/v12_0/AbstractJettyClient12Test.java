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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.client.StringRequestContent;
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

    AtomicReference<SpanContext> callerSpanContext = new AtomicReference<>();
    Runnable send =
        () -> {
          Context caller = Context.current().with(key, "caller");
          callerSpanContext.set(Span.fromContext(caller).getSpanContext());
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
    assertThat(Span.fromContext(observedContext).getSpanContext())
        .isEqualTo(callerSpanContext.get());
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
    CompletableFuture<Context> demandContext = new CompletableFuture<>();
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
                  Context callbackContext = Context.current();
                  testing.runWithSpan("response-body", () -> {});
                  demandContext.complete(callbackContext);
                } finally {
                  chunk.release();
                }
              });
          pendingSource.complete(source);
        });
    // Withhold this listener's demand so no content can satisfy the other listener's demand.
    request.onResponseContentSource((response, source) -> idleSource.complete(source));

    AtomicReference<SpanContext> callerSpanContext = new AtomicReference<>();
    Runnable send =
        () -> {
          Context caller = Context.current().with(key, "caller");
          callerSpanContext.set(Span.fromContext(caller).getSpanContext());
          try (Scope ignored = caller.makeCurrent()) {
            request.send(completed::complete);
          }
        };
    if (withParent) {
      testing.runWithSpan("parent", send::run);
    } else {
      send.run();
    }

    Content.Source pending = pendingSource.get(10, SECONDS);
    Content.Source idle = idleSource.get(10, SECONDS);
    assertThat(demandContext).isNotDone();
    testing.runWithSpan(
        "failing-thread",
        () -> {
          Context failingContext = Context.current().with(key, "failing-thread");
          try (Scope ignored = failingContext.makeCurrent()) {
            pending.fail(failure);
            assertThat(demandContext).isCompleted();
            assertThat(Span.current().getSpanContext())
                .isEqualTo(Span.fromContext(failingContext).getSpanContext());
            assertThat(Context.current().get(key)).isEqualTo("failing-thread");
            idle.fail(failure);
            assertThat(Span.current().getSpanContext())
                .isEqualTo(Span.fromContext(failingContext).getSpanContext());
            assertThat(Context.current().get(key)).isEqualTo("failing-thread");
          }
        });

    Context observedContext = demandContext.get(10, SECONDS);
    assertThat(observedContext.get(key)).isEqualTo("caller");
    assertThat(Span.fromContext(observedContext).getSpanContext())
        .isEqualTo(callerSpanContext.get());
    assertThat(completed.get(10, SECONDS).getFailure()).isSameAs(failure);

    Consumer<TraceAssert> operationTrace =
        trace -> {
          List<Consumer<SpanDataAssert>> spans = new ArrayList<>();
          if (withParent) {
            spans.add(span -> span.hasName("parent").hasNoParent());
          }
          int clientIndex = withParent ? 1 : 0;
          spans.add(
              span -> {
                span.hasName("GET").hasKind(SpanKind.CLIENT);
                if (withParent) {
                  span.hasParent(trace.getSpan(0));
                } else {
                  span.hasNoParent();
                }
              });
          spans.add(span -> span.hasName("test-http-server").hasParent(trace.getSpan(clientIndex)));
          if (withParent) {
            spans.add(span -> span.hasName("response-body").hasParent(trace.getSpan(0)));
          }
          trace.hasSpansSatisfyingExactly(spans);
        };
    Consumer<TraceAssert> failingThreadTrace =
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("failing-thread").hasNoParent());
    if (withParent) {
      testing.waitAndAssertTraces(operationTrace, failingThreadTrace);
    } else {
      testing.waitAndAssertTraces(
          operationTrace,
          failingThreadTrace,
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
  void lifecycleContexts(boolean withParent, String outcome, boolean throwingListeners)
      throws ReflectiveOperationException {
    ContextKey<String> key = ContextKey.named("jetty-test");
    Map<String, Context> requestContexts = new ConcurrentHashMap<>();
    Map<String, Context> responseContexts = new ConcurrentHashMap<>();
    CompletableFuture<Result> completed = new CompletableFuture<>();
    IOException failure = new IOException("request failed");
    Request request =
        client
            .newRequest(resolveAddress("/success"))
            .method("POST")
            .body(new StringRequestContent("request body"));
    request.listener(
        new Request.Listener() {
          private void record(String name) {
            requestContexts.put(name, Context.current());
            if (throwingListeners) {
              throw new IllegalStateException("request listener");
            }
          }

          @Override
          public void onQueued(Request request) {
            if (outcome.equals("inlineFailure")) {
              request.abort(failure);
            }
            record("queued");
          }

          @Override
          public void onBegin(Request request) {
            testing.runWithSpan("request-hook", () -> {});
            if (outcome.equals("asyncFailure")) {
              request.abort(failure);
            }
            record("begin");
          }

          @Override
          public void onHeaders(Request request) {
            record("headers");
          }

          @Override
          public void onCommit(Request request) {
            record("commit");
          }

          @Override
          public void onContent(Request request, ByteBuffer content) {
            record("content");
          }

          @Override
          public void onSuccess(Request request) {
            record("success");
          }

          @Override
          public void onFailure(Request request, Throwable cause) {
            if (outcome.equals("inlineFailure")) {
              testing.runWithSpan("request-hook", () -> {});
            }
            record("failure");
          }
        });
    class LifecycleListener
        implements Response.HeadersListener,
            Response.SuccessListener,
            Response.FailureListener,
            Response.CompleteListener {
      private void record(String name) {
        responseContexts.put(name, Context.current());
        if (throwingListeners) {
          throw new IllegalStateException("response listener");
        }
      }

      @Override
      public void onHeaders(Response response) {
        record("headers");
      }

      @Override
      public void onSuccess(Response response) {
        record("success");
      }

      @Override
      public void onFailure(Response response, Throwable cause) {
        record("failure");
      }

      @Override
      public void onComplete(Result result) {
        testing.runWithSpan("onComplete", () -> {});
        try {
          record("complete");
        } finally {
          completed.complete(result);
        }
      }
    }

    LifecycleListener listener = new LifecycleListener();
    request.onResponseHeaders(
        response -> responseContexts.put("registeredHeaders", Context.current()));
    request.onResponseSuccess(
        response -> responseContexts.put("registeredSuccess", Context.current()));
    request.onResponseFailure(
        (response, cause) -> responseContexts.put("registeredFailure", Context.current()));
    request.onComplete(result -> responseContexts.put("registeredComplete", Context.current()));
    if (testLatestDeps()) {
      Request.class
          .getMethod("onResponseListener", Response.Listener.class)
          .invoke(
              request,
              new Response.Listener() {
                @Override
                public void onComplete(Result result) {
                  responseContexts.put("aggregateComplete", Context.current());
                }
              });
    }

    Runnable send =
        () -> {
          Context caller = Context.current().with(key, "caller");
          try (Scope ignored = caller.makeCurrent()) {
            request.send(listener);
            if (outcome.equals("inlineFailure")) {
              assertThat(completed).isCompleted();
            }
            assertThat(Span.current().getSpanContext())
                .isEqualTo(Span.fromContext(caller).getSpanContext());
            assertThat(Context.current().get(key)).isEqualTo("caller");
          }
        };
    if (withParent) {
      testing.runWithSpan("parent", send::run);
    } else {
      send.run();
    }
    Result result = completed.join();
    if (testLatestDeps()) {
      assertThat(responseContexts).containsKey("aggregateComplete");
    }
    assertThat(result.isSucceeded()).isEqualTo(outcome.equals("success"));
    if (outcome.equals("success")) {
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    assertThat(Context.current().get(key)).isNull();

    if (outcome.equals("success")) {
      assertThat(requestContexts)
          .containsKeys("queued", "begin", "headers", "commit", "content", "success");
      assertThat(responseContexts)
          .containsKeys(
              "headers",
              "success",
              "complete",
              "registeredHeaders",
              "registeredSuccess",
              "registeredComplete");
    } else {
      assertThat(requestContexts).containsKey("failure");
      assertThat(responseContexts)
          .containsKeys("failure", "complete", "registeredFailure", "registeredComplete");
      assertThat(result.getFailure()).isSameAs(failure);
    }
    Consumer<TraceAssert> operationTrace =
        trace -> {
          int clientIndex = withParent ? 1 : 0;
          List<Consumer<SpanDataAssert>> spans = new ArrayList<>();
          if (withParent) {
            spans.add(span -> span.hasName("parent").hasNoParent());
          }
          spans.add(
              span -> {
                span.hasName("POST").hasKind(SpanKind.CLIENT);
                if (withParent) {
                  span.hasParent(trace.getSpan(0));
                } else {
                  span.hasNoParent();
                }
                SpanContext clientContext = trace.getSpan(clientIndex).getSpanContext();
                requestContexts
                    .values()
                    .forEach(
                        context -> {
                          assertThat(Span.fromContext(context).getSpanContext().getSpanId())
                              .isEqualTo(clientContext.getSpanId());
                          assertThat(context.get(key)).isEqualTo("caller");
                        });
                SpanContext callerContext =
                    withParent ? trace.getSpan(0).getSpanContext() : SpanContext.getInvalid();
                responseContexts
                    .values()
                    .forEach(
                        context -> {
                          assertThat(Span.fromContext(context).getSpanContext().getSpanId())
                              .isEqualTo(callerContext.getSpanId());
                          assertThat(context.get(key)).isEqualTo("caller");
                        });
              });
          spans.add(span -> span.hasName("request-hook").hasParent(trace.getSpan(clientIndex)));
          if (outcome.equals("success")) {
            spans.add(
                span -> span.hasName("test-http-server").hasParent(trace.getSpan(clientIndex)));
          }
          if (withParent) {
            spans.add(span -> span.hasName("onComplete").hasParent(trace.getSpan(0)));
          }
          trace.hasSpansSatisfyingExactly(spans);
        };
    if (withParent) {
      testing.waitAndAssertTraces(operationTrace);
    } else {
      testing.waitAndAssertTraces(
          operationTrace,
          trace ->
              trace.hasSpansSatisfyingExactly(span -> span.hasName("onComplete").hasNoParent()));
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
