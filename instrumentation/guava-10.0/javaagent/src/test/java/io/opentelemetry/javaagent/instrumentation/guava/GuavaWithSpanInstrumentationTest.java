/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
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
    SettableFuture<String> future = SettableFuture.create();
    new TracedWithSpan().listenableFuture(future);
    future.set("Value");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.listenableFuture")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributes(Attributes.empty())));
  }

  @Test
  void failure() {
    IllegalArgumentException error = new IllegalArgumentException("Boom");
    SettableFuture<String> future = SettableFuture.create();
    new TracedWithSpan().listenableFuture(future);
    future.setException(error);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.listenableFuture")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(error)
                        .hasAttributes(Attributes.empty())));
  }

  @Test
  void canceled() {
    SettableFuture<String> future = SettableFuture.create();
    new TracedWithSpan().listenableFuture(future);
    future.cancel(true);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.listenableFuture")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributes(attributeEntry("guava.canceled", true))));
  }

  @Test
  void immediateSuccess() {
    new TracedWithSpan().listenableFuture(Futures.immediateFuture("Value"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.listenableFuture")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributes(Attributes.empty())));
  }

  @Test
  void immediateFailure() {
    IllegalArgumentException error = new IllegalArgumentException("Boom");
    new TracedWithSpan().listenableFuture(Futures.immediateFailedFuture(error));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.listenableFuture")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(error)
                        .hasAttributes(Attributes.empty())));
  }
}
