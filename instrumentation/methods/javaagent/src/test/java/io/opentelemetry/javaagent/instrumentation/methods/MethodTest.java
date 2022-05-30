/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
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
    testing.clearData();
    new ConfigTracedCompletableFuture().getResult()
        .thenAccept(t -> assertThat(t).isEqualTo("Hello!"));
    testing.waitAndAssertTraces(
        trace -> {
          SpanData spanData = trace.getSpan(0);
          assertThat(spanData).isNotNull();
          assertThat(spanData.getName()).isEqualTo("ConfigTracedCompletableFuture.getResult");
          assertThat(spanData.getEndEpochNanos() - spanData.getStartEpochNanos())
              .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(100));
        });
  }


  static class ConfigTracedCompletableFuture {

    CompletableFuture<String> getResult() {
      CompletableFuture<String> completableFuture = new CompletableFuture<>();
      //can not use CompletableFuture#completeOnTimeout on language level 8
      new Thread(() -> {
        try {
          TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
          //ignore
        }
        completableFuture.complete("Hello!");
      }).start();
      return completableFuture;
    }
  }

  static class ConfigTracedCallable implements Callable<String> {

    @Override
    public String call() {
      return "Hello!";
    }
  }
}
