/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.AbstractJettyClient12Test;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.JettyClientTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JettyHttpClient12AgentTest extends AbstractJettyClient12Test {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Override
  protected HttpClient createStandardClient() {
    return new HttpClient();
  }

  @Override
  protected HttpClient createHttpsClient(SslContextFactory.Client sslContextFactory) {
    HttpClient httpClient = new HttpClient();
    httpClient.setSslContextFactory(sslContextFactory);
    return httpClient;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void libraryClientKeepsCallerContextForRequestHooks(boolean withParent) throws Exception {
    ContextKey<String> key = ContextKey.named("jetty-test");
    CompletableFuture<Context> hookContext = new CompletableFuture<>();
    CompletableFuture<Result> completed = new CompletableFuture<>();
    AtomicReference<SpanContext> callerSpanContext = new AtomicReference<>();
    HttpClient client = JettyClientTelemetry.create(testing.getOpenTelemetry()).createHttpClient();
    client.start();
    try {
      Request request = client.newRequest(resolveAddress("/success")).method("POST");
      request.onRequestBegin(
          ignored -> {
            hookContext.complete(Context.current());
            testing.runWithSpan("request-hook", () -> {});
          });
      Runnable send =
          () -> {
            Context caller =
                Baggage.builder()
                    .put("jetty-test", "caller")
                    .build()
                    .storeInContext(Context.current().with(key, "caller"));
            callerSpanContext.set(Span.fromContext(caller).getSpanContext());
            try (Scope ignored = caller.makeCurrent()) {
              request.send(completed::complete);
              assertThat(Context.current().get(key)).isEqualTo("caller");
              assertThat(Baggage.current().getEntryValue("jetty-test")).isEqualTo("caller");
            }
          };
      if (withParent) {
        testing.runWithSpan("parent", send::run);
      } else {
        send.run();
      }

      Result result = completed.get(10, SECONDS);
      assertThat(result.isSucceeded()).isTrue();
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      Context observedContext = hookContext.get(10, SECONDS);
      assertThat(observedContext.get(key)).isEqualTo("caller");
      assertThat(Baggage.fromContext(observedContext).getEntryValue("jetty-test"))
          .isEqualTo("caller");
      assertThat(Span.fromContext(observedContext).getSpanContext())
          .isEqualTo(callerSpanContext.get());

      if (withParent) {
        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactlyInAnyOrder(
                    span -> span.hasName("parent").hasNoParent(),
                    span ->
                        span.hasName("POST")
                            .hasKind(SpanKind.CLIENT)
                            .hasParentSpanId(callerSpanContext.get().getSpanId()),
                    span ->
                        span.hasName("request-hook")
                            .hasParentSpanId(callerSpanContext.get().getSpanId()),
                    span ->
                        span.hasName("test-http-server")
                            .hasParentSpanId(trace.getSpan(1).getSpanContext().getSpanId())));
      } else {
        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasNoParent(),
                    span -> span.hasName("test-http-server").hasParent(trace.getSpan(0))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("request-hook").hasNoParent()));
      }
    } finally {
      client.stop();
    }
  }
}
