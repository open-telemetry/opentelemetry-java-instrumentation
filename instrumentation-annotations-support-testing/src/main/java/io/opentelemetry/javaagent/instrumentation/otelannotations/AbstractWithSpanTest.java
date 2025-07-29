/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFunctionAssertions;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractWithSpanTest<T extends U, U> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  protected abstract AbstractTraced<T, U> newTraced();

  protected abstract void complete(T future, String value);

  protected abstract void fail(T future, Throwable error);

  protected abstract void cancel(T future);

  protected abstract String getCompleted(U future);

  protected abstract Throwable unwrapError(Throwable t);

  protected abstract String canceledKey();

  protected final InstrumentationExtension testing() {
    return testing;
  }

  @Test
  void success() {
    AbstractTraced<T, U> traced = newTraced();
    T future = traced.completable();
    complete(future, AbstractTraced.SUCCESS_VALUE);

    assertThat(getCompleted(future)).isEqualTo(AbstractTraced.SUCCESS_VALUE);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("Traced.completable")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            codeFunctionAssertions(traced.getClass(), "completable"))));
  }

  @Test
  void failure() {
    AbstractTraced<T, U> traced = newTraced();
    T future = traced.completable();
    fail(future, AbstractTraced.FAILURE);

    Throwable thrown = catchThrowable(() -> getCompleted(future));
    assertThat(unwrapError(thrown)).isEqualTo(AbstractTraced.FAILURE);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("Traced.completable")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(AbstractTraced.FAILURE)
                        .hasAttributesSatisfyingExactly(
                            codeFunctionAssertions(traced.getClass(), "completable"))));
  }

  @Test
  void canceled() {
    AbstractTraced<T, U> traced = newTraced();
    T future = traced.completable();
    cancel(future);

    List<AttributeAssertion> attributeAssertions =
        codeFunctionAssertions(traced.getClass(), "completable");
    attributeAssertions.add(equalTo(booleanKey(canceledKey()), true));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("Traced.completable")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(attributeAssertions)));
  }

  @Test
  void immediateSuccess() {
    AbstractTraced<T, U> traced = newTraced();
    assertThat(getCompleted(traced.alreadySucceeded())).isEqualTo(AbstractTraced.SUCCESS_VALUE);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("Traced.alreadySucceeded")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            codeFunctionAssertions(traced.getClass(), "alreadySucceeded"))));
  }

  @Test
  void immediateFailure() {
    AbstractTraced<T, U> traced = newTraced();
    Throwable error = catchThrowable(() -> getCompleted(traced.alreadyFailed()));
    assertThat(unwrapError(error)).isEqualTo(AbstractTraced.FAILURE);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("Traced.alreadyFailed")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(AbstractTraced.FAILURE)
                        .hasAttributesSatisfyingExactly(
                            codeFunctionAssertions(traced.getClass(), "alreadyFailed"))));
  }
}
