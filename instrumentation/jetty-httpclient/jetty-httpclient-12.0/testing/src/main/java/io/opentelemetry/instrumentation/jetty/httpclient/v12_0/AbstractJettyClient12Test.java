/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

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
import java.util.function.Consumer;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

  @Test
  void callbacksCalled() {
    URI uri = resolveAddress("/success");
    Request request = client.newRequest(uri).method("GET");

    CompletableFuture<Response> responseFuture = new CompletableFuture<>();
    TracingResponseListener responseListener = new TracingResponseListener(responseFuture);

    testing.runWithSpan("parent", () -> request.send(responseListener));
    Response response = responseFuture.join();

    assertThat(response.getStatus()).isEqualTo(200);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("onHeaders")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("onSuccess")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("onComplete")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
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
  void lifecycleContexts(boolean withParent, String outcome, boolean throwingListeners) {
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
    Response.Listener listener =
        new Response.Listener() {
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
            testing.runWithSpan("callback", () -> {});
            try {
              record("complete");
            } finally {
              completed.complete(result);
            }
          }
        };
    request.onResponseHeaders(response -> listener.onHeaders(response));
    request.onResponseSuccess(
        response -> responseContexts.put("registeredSuccess", Context.current()));
    request.onResponseFailure(
        (response, cause) -> responseContexts.put("registeredFailure", Context.current()));
    request.onComplete(result -> responseContexts.put("registeredComplete", Context.current()));

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
    assertThat(result.isSucceeded()).isEqualTo(outcome.equals("success"));
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    assertThat(Context.current().get(key)).isNull();

    if (outcome.equals("success")) {
      assertThat(requestContexts)
          .containsKeys("queued", "begin", "headers", "commit", "content", "success");
      assertThat(responseContexts)
          .containsKeys(
              "headers", "success", "complete", "registeredSuccess", "registeredComplete");
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
            spans.add(span -> span.hasName("callback").hasParent(trace.getSpan(0)));
          }
          trace.hasSpansSatisfyingExactly(spans);
        };
    if (withParent) {
      testing.waitAndAssertTraces(operationTrace);
    } else {
      testing.waitAndAssertTraces(
          operationTrace,
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("callback").hasNoParent()));
    }
  }

  private class TracingResponseListener
      implements Response.HeadersListener, Response.SuccessListener, Response.CompleteListener {

    private final CompletableFuture<Response> responseFuture;

    TracingResponseListener(CompletableFuture<Response> responseFuture) {
      this.responseFuture = responseFuture;
    }

    @Override
    public void onHeaders(Response response) {
      testing.runWithSpan("onHeaders", () -> {});
    }

    @Override
    public void onSuccess(Response response) {
      testing.runWithSpan("onSuccess", () -> {});
    }

    @Override
    public void onComplete(Result result) {
      testing.runWithSpan(
          "onComplete",
          () -> {
            if (result.isSucceeded()) {
              responseFuture.complete(result.getResponse());
            } else {
              responseFuture.completeExceptionally(result.getFailure());
            }
          });
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
