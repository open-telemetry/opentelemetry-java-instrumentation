/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import static java.nio.charset.StandardCharsets.UTF_8;
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
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Regression test for
 * https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/16405 Empty response
 * body when OTel instrumentation wraps Jetty HttpClient listeners on Jetty 9.4.24–~9.4.43.
 *
 * <p>In those versions, {@code AsyncContentListener} and {@code ContentListener} do NOT extend
 * {@code DemandedContentListener}. Jetty's {@code HttpReceiver.ContentListeners} filters via {@code
 * instanceof DemandedContentListener}, so a proxy that only implements the former interfaces gets
 * filtered out and content is never delivered.
 */
class JettyHttpClient9HttpCallTest {

  private static final String EXPECTED_BODY = "Hello World";
  private static final ContextKey<String> CALLER_KEY = ContextKey.named("caller");

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private HttpServer server;
  private HttpClient client;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body = EXPECTED_BODY.getBytes(UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    client = JettyClientTelemetry.builder(testing.getOpenTelemetry()).build().createHttpClient();
    client.start();
  }

  @AfterEach
  void stop() throws Exception {
    if (client != null) {
      client.stop();
    }
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void contentResponseBodyIsDelivered() throws Exception {
    String url = "http://localhost:" + server.getAddress().getPort() + "/";
    AtomicReference<Context> preparationContext = new AtomicReference<>();
    observePreparation(() -> preparationContext.set(Context.current()));

    Context enteringContext = Context.current();
    Span callerSpan =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("caller").startSpan();
    Context callerContext = enteringContext.with(callerSpan).with(CALLER_KEY, "caller");
    Span clientSpan;
    try {
      ContentResponse response;
      try (Scope ignored = callerContext.makeCurrent()) {
        response = client.newRequest(url).send();
        assertThat(Context.current()).isSameAs(callerContext);
      }
      assertThat(Context.current()).isSameAs(enteringContext);

      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.getContentAsString()).isEqualTo(EXPECTED_BODY);
      Context preparedContext = preparationContext.get();
      assertThat(preparedContext.get(CALLER_KEY)).isEqualTo("caller");
      assertThat(preparedContext).isNotSameAs(callerContext);
      clientSpan = Span.fromContext(preparedContext);
      assertThat(clientSpan.getSpanContext().isValid()).isTrue();
    } finally {
      callerSpan.end();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("caller"),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasSpanId(clientSpan.getSpanContext().getSpanId())));
  }

  @Test
  void exceptionalSendRestoresCallerContext() {
    RuntimeException failure = new IllegalStateException("send preparation failed");
    AtomicReference<Context> preparationContext = new AtomicReference<>();
    observePreparation(
        () -> {
          preparationContext.set(Context.current());
          throw failure;
        });

    Context enteringContext = Context.current();
    Context callerContext = enteringContext.with(CALLER_KEY, "caller");
    try {
      try (Scope ignored = callerContext.makeCurrent()) {
        assertThatThrownBy(() -> client.newRequest(url()).send(result -> {})).isSameAs(failure);
        assertThat(Context.current()).isSameAs(callerContext);
      }
      assertThat(Context.current()).isSameAs(enteringContext);
      assertThat(preparationContext.get()).isNotSameAs(callerContext);
    } finally {
      Span.fromContext(preparationContext.get()).end();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("GET").hasKind(SpanKind.CLIENT)));
  }

  @Test
  void sendWithoutStartedSpanPreservesContext() throws Exception {
    client.stop();
    Instrumenter<Request, Response> instrumenter =
        Instrumenter.<Request, Response>builder(
                testing.getOpenTelemetry(), "test", request -> "unused")
            .setEnabled(false)
            .buildInstrumenter();
    client = new TracingHttpClient(instrumenter);
    AtomicReference<Context> preparationContext = new AtomicReference<>();
    observePreparation(() -> preparationContext.set(Context.current()));
    client.start();

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
  void inputStreamListenerBodyIsDelivered() throws Exception {
    String url = "http://localhost:" + server.getAddress().getPort() + "/";

    InputStreamResponseListener listener = new InputStreamResponseListener();
    client.newRequest(url).send(listener);

    Response response = listener.get(30, SECONDS);
    assertThat(response.getStatus()).isEqualTo(200);

    byte[] body;
    try (InputStream is = listener.getInputStream()) {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      byte[] tmp = new byte[4096];
      int n;
      while ((n = is.read(tmp)) != -1) {
        buf.write(tmp, 0, n);
      }
      body = buf.toByteArray();
    }

    assertThat(body).isNotEmpty();
    assertThat(new String(body, UTF_8)).isEqualTo(EXPECTED_BODY);
  }

  private void observePreparation(Runnable observer) {
    client
        .getProxyConfiguration()
        .getProxies()
        .add(
            new HttpProxy("localhost", 1) {
              @Override
              public boolean matches(Origin origin) {
                observer.run();
                return false;
              }
            });
  }

  private String url() {
    return "http://localhost:" + server.getAddress().getPort() + "/";
  }
}
