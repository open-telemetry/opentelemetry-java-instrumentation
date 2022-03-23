/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// TODO: add a test for a longer chain of promises
class ListenableFutureTest {

  static final ExecutorService executor = Executors.newSingleThreadExecutor();

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @AfterAll
  static void shutdown() {
    executor.shutdown();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void callWithParent(boolean value) {
    SettableFuture<Boolean> future = SettableFuture.create();

    testing.runWithSpan(
        "parent",
        () -> {
          ListenableFuture<String> mapped = Futures.transform(future, String::valueOf, executor);
          Futures.addCallback(
              mapped,
              new FutureCallback<String>() {
                @Override
                public void onSuccess(String result) {
                  assertThat(result).isEqualTo(String.valueOf(value));
                  testing.runWithSpan("callback", () -> {});
                }

                @Override
                public void onFailure(Throwable t) {
                  throw new AssertionError(t);
                }
              },
              executor);
          testing.runWithSpan("other", () -> future.set(value));
        });

    assertThat(Futures.getUnchecked(future)).isEqualTo(value);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("other").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void callWithParentCompleteOutsideSpan(boolean value) {
    SettableFuture<Boolean> future = SettableFuture.create();

    testing.runWithSpan(
        "parent",
        () -> {
          ListenableFuture<String> mapped = Futures.transform(future, String::valueOf, executor);
          Futures.addCallback(
              mapped,
              new FutureCallback<String>() {
                @Override
                public void onSuccess(String result) {
                  assertThat(result).isEqualTo(String.valueOf(value));
                  testing.runWithSpan("callback", () -> {});
                }

                @Override
                public void onFailure(Throwable t) {
                  throw new AssertionError(t);
                }
              },
              executor);
        });

    testing.runWithSpan("other", () -> future.set(value));

    assertThat(Futures.getUnchecked(future)).isEqualTo(value);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("other").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void callWithParentCompleteOnSeparateThread(boolean value) {

    SettableFuture<Boolean> future = SettableFuture.create();

    testing.runWithSpan(
        "parent",
        () -> {
          ListenableFuture<String> mapped = Futures.transform(future, String::valueOf, executor);
          Futures.addCallback(
              mapped,
              new FutureCallback<String>() {
                @Override
                public void onSuccess(String result) {
                  assertThat(result).isEqualTo(String.valueOf(value));
                  testing.runWithSpan("callback", () -> {});
                }

                @Override
                public void onFailure(Throwable t) {
                  throw new AssertionError(t);
                }
              },
              executor);
          executor.submit(() -> future.set(value));
        });

    assertThat(Futures.getUnchecked(future)).isEqualTo(value);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void callWithNoParent(boolean value) {
    SettableFuture<Boolean> future = SettableFuture.create();

    ListenableFuture<String> mapped = Futures.transform(future, String::valueOf, executor);
    Futures.addCallback(
        mapped,
        new FutureCallback<String>() {
          @Override
          public void onSuccess(String result) {
            assertThat(result).isEqualTo(String.valueOf(value));
            testing.runWithSpan("callback", () -> {});
          }

          @Override
          public void onFailure(Throwable t) {
            throw new AssertionError(t);
          }
        },
        executor);
    testing.runWithSpan("other", () -> future.set(value));

    assertThat(Futures.getUnchecked(future)).isEqualTo(value);

    // TODO: There appears to be a context propagation bug. There is no logical reason for other to
    // be the parent of
    // callback in this test but not in callWithParent.
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("other").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }
}
