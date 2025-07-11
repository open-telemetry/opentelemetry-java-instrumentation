/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extannotations;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFunctionAssertions;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentracing.contrib.dropwizard.Trace;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TraceAnnotationsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testSimpleCaseAnnotations() {
    // Test single span in new trace
    SayTracedHello.sayHello();

    List<AttributeAssertion> assertions = assertCodeFunction("sayHello");
    assertions.add(equalTo(stringKey("myattr"), "test"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SayTracedHello.sayHello")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(assertions)));
  }

  private static List<AttributeAssertion> assertCodeFunction(String methodName) {
    return codeFunctionAssertions(SayTracedHello.class, methodName);
  }

  @Test
  void testComplexCaseAnnotations() {
    // Test new trace with 2 children spans
    SayTracedHello.sayHelloSayHa();
    List<AttributeAssertion> assertions1 = assertCodeFunction("sayHelloSayHa");
    assertions1.add(equalTo(stringKey("myattr"), "test2"));
    List<AttributeAssertion> assertions2 = assertCodeFunction("sayHello");
    assertions2.add(equalTo(stringKey("myattr"), "test"));
    List<AttributeAssertion> assertions3 = assertCodeFunction("sayHello");
    assertions3.add(equalTo(stringKey("myattr"), "test"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SayTracedHello.sayHelloSayHa")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(assertions1),
                span ->
                    span.hasName("SayTracedHello.sayHello")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(assertions2),
                span ->
                    span.hasName("SayTracedHello.sayHello")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(assertions3)));
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
                        .hasAttributesSatisfyingExactly(assertCodeFunction("sayError"))));
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
                            codeFunctionAssertions(
                                SayTracedHello.class.getName() + "$1", "call"))));

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
                            codeFunctionAssertions(SayTracedHello.class.getName() + "$1", "call"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TraceAnnotationsTest$1.call")
                        .hasAttributesSatisfyingExactly(
                            codeFunctionAssertions(
                                TraceAnnotationsTest.class.getName() + "$1", "call"))));
  }
}
