/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MethodTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void methodTraced() {
    assertThat(new ConfigTracedCallable().call()).isEqualTo("Hello!");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ConfigTracedCallable.call")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributes(Attributes.empty())));
  }

  static class ConfigTracedCallable implements Callable<String> {

    @Override
    public String call() {
      return "Hello!";
    }
  }

  @Test
  void methodTracedWithAsyncStop() throws Exception {
    ConfigTracedCompletableFuture traced = new ConfigTracedCompletableFuture();
    CompletableFuture<String> future = traced.getResult();

    // span is ended when CompletableFuture is completed
    // verify that span has not been ended yet
    assertThat(traced.span).isNotNull().satisfies(span -> assertThat(span.isRecording()).isTrue());

    traced.countDownLatch.countDown();
    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("Hello!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ConfigTracedCompletableFuture.getResult")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributes(Attributes.empty())));
  }

  static class ConfigTracedCompletableFuture {
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    Span span;

    CompletableFuture<String> getResult() {
      CompletableFuture<String> completableFuture = new CompletableFuture<>();
      span = Span.current();
      new Thread(
              () -> {
                try {
                  countDownLatch.await();
                } catch (InterruptedException exception) {
                  // ignore
                }
                completableFuture.complete("Hello!");
              })
          .start();
      return completableFuture;
    }
  }
}
