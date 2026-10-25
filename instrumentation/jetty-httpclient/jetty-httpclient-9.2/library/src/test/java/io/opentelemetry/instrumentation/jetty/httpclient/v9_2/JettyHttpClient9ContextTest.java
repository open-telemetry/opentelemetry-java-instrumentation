/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JettyHttpClient9ContextTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final ContextKey<String> CALLER_KEY = ContextKey.named("caller");

  private HttpServer server;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress(PortUtils.findOpenPort()), 0);
    cleanup.deferCleanup(() -> server.stop(0));
    server.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void delegatedSendUsesClientContext(boolean withCallerSpan) throws Exception {
    HttpClient client = JettyClientTelemetry.create(testing.getOpenTelemetry()).createHttpClient();
    AtomicReference<Context> preparationContext = new AtomicReference<>();
    observePreparation(client, () -> preparationContext.set(Context.current()));
    startClient(client);

    Span callerSpan =
        withCallerSpan
            ? testing.getOpenTelemetry().getTracer("test").spanBuilder("caller").startSpan()
            : Span.getInvalid();
    Context enteringContext = Context.current();
    Context callerContext = enteringContext.with(callerSpan).with(CALLER_KEY, "caller");
    AtomicReference<Context> requestContext = new AtomicReference<>();
    AtomicReference<Context> responseContext = new AtomicReference<>();
    AtomicReference<Context> completionContext = new AtomicReference<>();
    CompletableFuture<Result> completed = new CompletableFuture<>();
    Request request =
        client
            .newRequest(url())
            .onRequestBegin(ignored -> requestContext.set(Context.current()))
            .onResponseBegin(ignored -> responseContext.set(Context.current()));

    try {
      try (Scope ignored = callerContext.makeCurrent()) {
        request.send(
            result -> {
              completionContext.set(Context.current());
              completed.complete(result);
            });
        assertThat(Context.current()).isSameAs(callerContext);
      }
      assertThat(Context.current()).isSameAs(enteringContext);

      Result result = completed.get(10, SECONDS);
      assertThat(result.isSucceeded()).isTrue();
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      assertThat(preparationContext.get()).isNotSameAs(callerContext);
      assertThat(preparationContext.get().get(CALLER_KEY)).isEqualTo("caller");
      assertThat(requestContext.get()).isSameAs(preparationContext.get());
      assertThat(responseContext.get()).isSameAs(callerContext);
      assertThat(completionContext.get()).isSameAs(callerContext);
    } finally {
      callerSpan.end();
    }

    if (withCallerSpan) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("caller"),
                  span ->
                      span.hasName("GET")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasSpanId(
                              Span.fromContext(preparationContext.get())
                                  .getSpanContext()
                                  .getSpanId())));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("GET")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasSpanId(
                              Span.fromContext(preparationContext.get())
                                  .getSpanContext()
                                  .getSpanId())));
    }
  }

  @Test
  void delegatedSendWithoutStartedSpanPreservesContext() throws Exception {
    Instrumenter<Request, Response> instrumenter =
        Instrumenter.<Request, Response>builder(
                testing.getOpenTelemetry(), "test", request -> "unused")
            .setEnabled(false)
            .buildInstrumenter();
    HttpClient client = new TracingHttpClient(instrumenter);
    AtomicReference<Context> preparationContext = new AtomicReference<>();
    observePreparation(client, () -> preparationContext.set(Context.current()));
    startClient(client);

    testing.runWithSpan(
        "caller",
        () -> {
          Context callerContext = Context.current();
          assertThat(client.newRequest(url()).send().getStatus()).isEqualTo(200);
          assertThat(preparationContext.get()).isSameAs(callerContext);
          assertThat(Context.current()).isSameAs(callerContext);
        });

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("caller")));
  }

  @Test
  void exceptionalDelegatedSendRestoresCallerContext() throws Exception {
    HttpClient client = JettyClientTelemetry.create(testing.getOpenTelemetry()).createHttpClient();
    RuntimeException failure = new IllegalStateException("send preparation failed");
    AtomicReference<Context> preparationContext = new AtomicReference<>();
    observePreparation(
        client,
        () -> {
          preparationContext.set(Context.current());
          throw failure;
        });
    startClient(client);

    Context enteringContext = Context.current();
    Context callerContext = enteringContext.with(CALLER_KEY, "caller");
    try {
      try (Scope ignored = callerContext.makeCurrent()) {
        assertThatThrownBy(() -> client.newRequest(url()).send(result -> {})).isSameAs(failure);
        assertThat(Context.current()).isSameAs(callerContext);
      }
      assertThat(Context.current()).isSameAs(enteringContext);
      assertThat(preparationContext.get()).isNotSameAs(callerContext);
      assertThat(Span.fromContext(preparationContext.get()).getSpanContext().isValid()).isTrue();
    } finally {
      // Preparation failed before Jetty could notify the tracing listener.
      Span.fromContext(preparationContext.get()).end();
    }
  }

  private static void observePreparation(HttpClient client, Runnable observer) {
    client
        .getProxyConfiguration()
        .getProxies()
        .add(
            new HttpProxy("localhost", 1) {
              @Override
              public boolean matches(Origin origin) {
                // Destination preparation runs inside HttpClient.send(), before async dispatch.
                observer.run();
                return false;
              }
            });
  }

  private static void startClient(HttpClient client) throws Exception {
    cleanup.deferCleanup(client::stop);
    client.start();
  }

  private String url() {
    return "http://localhost:" + server.getAddress().getPort() + "/";
  }
}
