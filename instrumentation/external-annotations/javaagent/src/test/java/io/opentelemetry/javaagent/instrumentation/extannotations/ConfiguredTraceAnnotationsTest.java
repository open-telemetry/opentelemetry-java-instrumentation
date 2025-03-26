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
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ConfiguredTraceAnnotationsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testMethodWithDisabledNewRelicAnnotationShouldBeIgnored() {
    SayTracedHello.fromCallableWhenDisabled();
    assertThat(testing.spans()).isEmpty();
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void testMethodWithAnnotationShouldBeTraced() {
    assertThat(new AnnotationTracedCallable().call()).isEqualTo("Hello!");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("AnnotationTracedCallable.call")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                AnnotationTracedCallable.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "call"))));
  }

  static class AnnotationTracedCallable implements Callable<String> {
    @OuterClass.InterestingMethod
    @Override
    public String call() {
      return "Hello!";
    }
  }
}
