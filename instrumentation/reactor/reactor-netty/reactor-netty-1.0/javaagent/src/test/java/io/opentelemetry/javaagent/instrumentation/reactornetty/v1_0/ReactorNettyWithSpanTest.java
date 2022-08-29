/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

class ReactorNettyWithSpanTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static HttpClientTestServer server;

  @BeforeAll
  static void setUp() {
    server = new HttpClientTestServer(testing.getOpenTelemetry());
    server.start();
  }

  @AfterAll
  static void tearDown() {
    server.stop();
  }

  @Test
  public void testSuccessfulNestedUnderWithSpan() {
    HttpClient httpClient = HttpClient.create();

    Mono<Integer> httpRequest =
        Mono.defer(
            () ->
                httpClient
                    .get()
                    .uri("http://localhost:" + server.httpPort() + "/success")
                    .responseSingle(
                        (resp, content) -> {
                          // Make sure to consume content since that's when we close the span.
                          return content.map(unused -> resp);
                        })
                    .map(r -> r.status().code()));

    Mono<Integer> getResponse =
        new TracedWithSpan()
            .mono(
                // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4348
                // our HTTP server is synchronous, i.e. it returns Mono.just with response
                // which is not supported by TracingSubscriber - it does not instrument scalar calls
                // so we delay here to fake async http request and let Reactor context
                // instrumentation work
                Mono.delay(Duration.ofMillis(1)).then(httpRequest));

    StepVerifier.create(getResponse).expectNext(200).expectComplete().verify();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("TracedWithSpan.mono").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasName("HTTP GET").hasKind(CLIENT).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(1))));
  }
}
