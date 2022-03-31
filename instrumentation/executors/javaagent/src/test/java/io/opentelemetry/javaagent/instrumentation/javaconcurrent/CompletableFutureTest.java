/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaconcurrent;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CompletableFutureTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void multipleCallbacks() {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    ExecutorService executor2 = Executors.newSingleThreadExecutor();

    String result;
    try {
      result =
          testing.runWithSpan(
              "parent",
              () ->
                  CompletableFuture.supplyAsync(
                          () -> {
                            testing.runWithSpan("supplier", () -> {});
                            try {
                              Thread.sleep(1);
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new AssertionError(e);
                            }
                            return "a";
                          },
                          executor)
                      .thenCompose(
                          s -> CompletableFuture.supplyAsync(new AppendingSupplier(s), executor2))
                      .thenApply(
                          s -> {
                            testing.runWithSpan("function", () -> {});
                            return s + "c";
                          })
                      .get());
    } catch (Exception e) {
      throw new AssertionError(e);
    }

    assertThat(result).isEqualTo("abc");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("supplier").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("appendingSupplier")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("function")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));

    executor.shutdown();
    executor2.shutdown();
  }

  @Test
  void supplyAsync() {
    CompletableFuture<String> future =
        testing.runWithSpan(
            "parent",
            () -> CompletableFuture.supplyAsync(() -> testing.runWithSpan("child", () -> "done")));

    assertThat(future.join()).isEqualTo("done");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));
  }

  @Test
  void thenApply() {
    CompletableFuture<String> future =
        testing.runWithSpan(
            "parent",
            () ->
                CompletableFuture.supplyAsync(() -> "done")
                    .thenApply(result -> testing.runWithSpan("child", () -> result)));

    assertThat(future.join()).isEqualTo("done");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));
  }

  @Test
  void thenApplyAsync() {
    CompletableFuture<String> future =
        testing.runWithSpan(
            "parent",
            () ->
                CompletableFuture.supplyAsync(() -> "done")
                    .thenApplyAsync(result -> testing.runWithSpan("child", () -> result)));

    assertThat(future.join()).isEqualTo("done");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));
  }

  @Test
  void thenCompose() {
    CompletableFuture<String> future =
        testing.runWithSpan(
            "parent",
            () ->
                CompletableFuture.supplyAsync(() -> "done")
                    .thenCompose(
                        result ->
                            CompletableFuture.supplyAsync(
                                () -> testing.runWithSpan("child", () -> result))));

    assertThat(future.join()).isEqualTo("done");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));
  }

  @Test
  void thenComposeAsync() {
    CompletableFuture<String> future =
        testing.runWithSpan(
            "parent",
            () ->
                CompletableFuture.supplyAsync(() -> "done")
                    .thenComposeAsync(
                        result ->
                            CompletableFuture.supplyAsync(
                                () -> testing.runWithSpan("child", () -> result))));

    assertThat(future.join()).isEqualTo("done");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));
  }

  @Test
  void thenComposeAndApply() {
    CompletableFuture<String> future =
        testing.runWithSpan(
            "parent",
            () ->
                CompletableFuture.supplyAsync(() -> "do")
                    .thenCompose(result -> CompletableFuture.supplyAsync(() -> result + "ne"))
                    .thenApplyAsync(result -> testing.runWithSpan("child", () -> result)));

    assertThat(future.join()).isEqualTo("done");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));
  }

  static final class AppendingSupplier implements Supplier<String> {

    private final String letter;

    AppendingSupplier(String letter) {
      this.letter = letter;
    }

    @Override
    public String get() {
      testing.runWithSpan("appendingSupplier", () -> {});
      return letter + "b";
    }
  }
}
