/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extannotations;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import io.opentracing.contrib.dropwizard.Trace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TracedMethodsExclusionTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void testCallingTheseMethodsShouldBeTraced() {
    // Baseline and assumption validation
    assertThat(new TestClass().annotated()).isEqualTo("Hello!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TestClass.annotated")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE, TestClass.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "annotated"))));
  }

  @Test
  void testNotExplicitlyConfiguredMethodShouldNotBeTraced() throws InterruptedException {
    assertThat(new TestClass().notMentioned()).isEqualTo("Hello!");

    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void testMethodWhichBothAnnotatedAndExcludedForTracingShouldNotBeTraced()
      throws InterruptedException {

    assertThat(new TestClass().excluded()).isEqualTo("Hello!");
    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void testMethodExclusionShouldOverrideTracingAnnotations() throws InterruptedException {

    assertThat(new TestClass().annotatedButExcluded()).isEqualTo("Hello!");
    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.spans()).isEmpty();
  }

  static class TestClass {
    // This method is not mentioned in any configuration
    String notMentioned() {
      return "Hello!";
    }

    // This method is both configured to be traced and to be excluded. Should NOT be traced.
    String excluded() {
      return "Hello!";
    }

    // This method is annotated with annotation which usually results in a captured span
    @Trace
    String annotated() {
      return "Hello!";
    }

    // This method is annotated with annotation which usually results in a captured span, but is
    // configured to be
    // excluded.
    @Trace
    String annotatedButExcluded() {
      return "Hello!";
    }
  }
}
