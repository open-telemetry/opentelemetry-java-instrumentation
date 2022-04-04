/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class GuavaWithSpanInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void success() {
    SettableFuture<String> future = new TracedWithSpan().completable();
    future.set("Value");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completable")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributes(Attributes.empty())));
  }

  @Test
  void failure() {
    IllegalArgumentException error = new IllegalArgumentException("Boom");
    SettableFuture<String> future = new TracedWithSpan().completable();
    future.setException(error);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completable")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(error)
                        .hasAttributes(Attributes.empty())));
  }

  @Test
  void canceled() {
    SettableFuture<String> future = new TracedWithSpan().completable();
    future.cancel(true);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completable")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributes(attributeEntry("guava.canceled", true))));
  }

  @Test
  void immediateSuccess() {
    assertThat(Futures.getUnchecked(new TracedWithSpan().alreadySucceeded())).isEqualTo("Value");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.alreadySucceeded")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributes(Attributes.empty())));
  }

  @Test
  void immediateFailure() {
    assertThatThrownBy(() -> Futures.getUnchecked(new TracedWithSpan().alreadyFailed()))
        .isInstanceOf(UncheckedExecutionException.class)
        .hasCause(TracedWithSpan.FAILURE);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.alreadyFailed")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(TracedWithSpan.FAILURE)
                        .hasAttributes(Attributes.empty())));
  }
}
