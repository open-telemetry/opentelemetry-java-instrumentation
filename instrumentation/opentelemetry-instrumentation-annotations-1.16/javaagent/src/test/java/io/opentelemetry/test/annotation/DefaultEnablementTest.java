/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DefaultEnablementTest {

  private static final boolean INSTRUMENTATION_ENABLED =
      Boolean.parseBoolean(
          System.getProperty(
              "otel.instrumentation.opentelemetry-instrumentation-annotations.enabled", "true"));

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @Test
  void defaultEnablement() {
    testing.runWithSpan("parent", DefaultEnablementTest::annotated);

    if (INSTRUMENTATION_ENABLED) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasNoParent(),
                  span ->
                      span.hasName("annotated")
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(
                                  CODE_FUNCTION_NAME,
                                  DefaultEnablementTest.class.getName() + ".annotated"))));
    } else {
      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent").hasNoParent()));
    }
  }

  @WithSpan("annotated")
  private static void annotated() {}
}
