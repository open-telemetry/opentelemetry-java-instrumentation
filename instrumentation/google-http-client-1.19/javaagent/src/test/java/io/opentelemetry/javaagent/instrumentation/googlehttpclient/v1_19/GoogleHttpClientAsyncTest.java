/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient.v1_19;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.api.trace.SpanKind;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class GoogleHttpClientAsyncTest extends AbstractGoogleHttpClientTest {

  private final ExecutorService executor = Executors.newFixedThreadPool(4);

  @AfterAll
  void tearDown() {
    executor.shutdown();
  }

  @Override
  protected HttpResponse sendRequest(HttpRequest request) throws Exception {
    return request.executeAsync(executor).get();
  }

  @Test
  void reusesRequestAfterAsyncExecution() throws Exception {
    HttpRequest request = buildRequest("GET", resolveAddress("/success"), emptyMap());

    testing.runWithSpan("first", () -> request.executeAsync(executor).get().parseAsString());
    testing.runWithSpan("second", () -> request.execute().parseAsString());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("first"),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(1))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("second"),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(1))));
  }

  @Test
  void reusesRequestAfterRejectedSubmission() throws Exception {
    HttpRequest request = buildRequest("GET", resolveAddress("/success"), emptyMap());

    testing.runWithSpan(
        "rejected",
        () ->
            assertThatThrownBy(
                    () ->
                        request.executeAsync(command -> {
                          throw new RejectedExecutionException("test");
                        }))
                .isInstanceOf(RejectedExecutionException.class));
    testing.runWithSpan("retry", () -> request.execute().parseAsString());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("rejected"),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("retry"),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(1))));
  }
}
