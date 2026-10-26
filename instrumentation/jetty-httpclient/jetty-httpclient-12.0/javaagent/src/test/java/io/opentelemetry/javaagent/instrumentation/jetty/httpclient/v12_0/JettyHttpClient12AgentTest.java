/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.AbstractJettyClient12Test;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.JettyClientTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import java.util.concurrent.CompletableFuture;
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
  void libraryClientKeepsCallerContextForResponseListeners(boolean withParent) throws Exception {
    CompletableFuture<Result> completed = new CompletableFuture<>();
    HttpClient client = JettyClientTelemetry.create(testing.getOpenTelemetry()).createHttpClient();
    client.start();
    try {
      Request request = client.newRequest(resolveAddress("/success")).method("POST");
      request.onResponseHeaders(ignored -> testing.runWithSpan("response-headers", () -> {}));
      Runnable send =
          () ->
              request.send(
                  result -> {
                    testing.runWithSpan("response-complete", () -> {});
                    completed.complete(result);
                  });
      if (withParent) {
        testing.runWithSpan("parent", send::run);
      } else {
        send.run();
      }

      Result result = completed.get(10, SECONDS);
      assertThat(result.isSucceeded()).isTrue();
      assertThat(result.getResponse().getStatus()).isEqualTo(200);

      if (withParent) {
        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactlyInAnyOrder(
                    span -> span.hasName("parent").hasNoParent(),
                    span ->
                        span.hasName("POST").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("POST").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                    span -> span.hasName("response-headers").hasParent(trace.getSpan(0)),
                    span -> span.hasName("response-complete").hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("test-http-server")
                            .hasParentSpanId(trace.getSpan(2).getSpanContext().getSpanId())));
      } else {
        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasNoParent()),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasNoParent(),
                    span -> span.hasName("test-http-server").hasParent(trace.getSpan(0))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("response-headers").hasNoParent()),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("response-complete").hasNoParent()));
      }
    } finally {
      client.stop();
    }
  }
}
