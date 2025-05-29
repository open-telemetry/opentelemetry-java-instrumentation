/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extannotations;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import io.opentracing.contrib.dropwizard.Trace;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class TraceAnnotationsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testSimpleCaseAnnotations() {
    // Test single span in new trace
    SayTracedHello.sayHello();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SayTracedHello.sayHello")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                SayTracedHello.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "sayHello"),
                            equalTo(stringKey("myattr"), "test"))));
  }

  @Test
  void testComplexCaseAnnotations() {
    // Test new trace with 2 children spans
    SayTracedHello.sayHelloSayHa();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SayTracedHello.sayHelloSayHa")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                SayTracedHello.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "sayHelloSayHa"),
                            equalTo(stringKey("myattr"), "test2")),
                span ->
                    span.hasName("SayTracedHello.sayHello")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                SayTracedHello.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "sayHello"),
                            equalTo(stringKey("myattr"), "test")),
                span ->
                    span.hasName("SayTracedHello.sayHello")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                SayTracedHello.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "sayHello"),
                            equalTo(stringKey("myattr"), "test"))));
  }

  @Test
  void testExceptionExit() {
    Throwable thrown = catchThrowable(SayTracedHello::sayError);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SayTracedHello.sayError")
                        .hasStatus(StatusData.error())
                        .hasException(thrown)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                SayTracedHello.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "sayError"))));
  }

  @Test
  void testAnonymousClassAnnotations() {
    // Test anonymous classes with package
    SayTracedHello.fromCallable();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SayTracedHello$1.call")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                SayTracedHello.class.getName() + "$1"),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "call"))));

    // Test anonymous classes with no package
    new Callable<String>() {
      @Trace
      @Override
      public String call() {
        return "Howdy!";
      }
    }.call();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SayTracedHello$1.call")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                SayTracedHello.class.getName() + "$1"),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "call"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TraceAnnotationsTest$1.call")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TraceAnnotationsTest.class.getName() + "$1"),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "call"))));
  }
}
