/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
