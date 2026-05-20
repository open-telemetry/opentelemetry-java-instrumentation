/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AddingSpanAttributesInstrumentationTest {

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void captureAttributesInNewSpan() {
    testing.runWithSpan(
        "root",
        () ->
            new ExtractAttributesUsingAddingSpanAttributes()
                .withSpanTakesPrecedence("foo", "bar", null, "baz"));

    List<AttributeAssertion> attributesAssertions =
        new ArrayList<>(
            SemconvCodeStabilityUtil.codeFunctionAssertions(
                ExtractAttributesUsingAddingSpanAttributes.class, "withSpanTakesPrecedence"));
    attributesAssertions.add(equalTo(stringKey("implicitName"), "foo"));
    attributesAssertions.add(equalTo(stringKey("explicitName"), "bar"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("root").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(
                            "ExtractAttributesUsingAddingSpanAttributes.withSpanTakesPrecedence")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParentSpanId(trace.getSpan(0).getSpanId())
                        .hasAttributesSatisfyingExactly(attributesAssertions)));
  }

  @Test
  void captureAttributesInCurrentSpan() {
    testing.runWithSpan(
        "root",
        () ->
            new ExtractAttributesUsingAddingSpanAttributes()
                .withSpanAttributes("foo", "bar", null, "baz"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("root")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("implicitName"), "foo"),
                            equalTo(stringKey("explicitName"), "bar"))));
  }

  @Test
  void noExistingSpan() {
    new ExtractAttributesUsingAddingSpanAttributes().withSpanAttributes("foo", "bar", null, "baz");

    assertThat(testing.waitForTraces(0)).isEmpty();
  }

  @Test
  void constructorOnlyAddingSpanAttributesDoesNotTransformType() {
    // Current behavior: the type matcher only considers declared methods, so a class with only an
    // @AddingSpanAttributes constructor is not transformed.
    testing.runWithSpan(
        "root", () -> new ConstructorOnlyAddingSpanAttributes("foo", "bar", null, "baz"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("root")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasTotalAttributeCount(0)));
  }

  @Test
  void constructorAndMethodAddingSpanAttributesDoesNotAddAttributes() {
    // Current behavior: once the annotated method makes the class eligible for transformation, the
    // annotated constructor is also matched and fails because @Advice.Origin Method cannot
    // represent constructors.
    TestAgentListenerAccess.addSkipErrorCondition(
        (typeName, t) ->
            typeName.equals(ConstructedWithAddingSpanAttributes.class.getName())
                && t.getMessage() != null
                && t.getMessage().startsWith("Cannot represent ")
                && t.getMessage().endsWith(" as the specified constant"));

    try {
      testing.runWithSpan(
          "root",
          () ->
              new ConstructedWithAddingSpanAttributes("foo", "bar", null, "baz")
                  .addAttributes("method"));

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("root")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasTotalAttributeCount(0)));
    } finally {
      TestAgentListenerAccess.reset();
    }
  }

  @Test
  void overwriteAttributes() {
    testing.runWithSpan(
        "root",
        () -> {
          Span.current().setAttribute("implicitName", "willbegone");
          Span.current().setAttribute("keep", "willbekept");
          new ExtractAttributesUsingAddingSpanAttributes()
              .withSpanAttributes("foo", "bar", null, "baz");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("root")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("keep"), "willbekept"),
                            equalTo(stringKey("implicitName"), "foo"),
                            equalTo(stringKey("explicitName"), "bar"))));
  }

  @Test
  void multiMethodOverwriteAttributes() {
    testing.runWithSpan(
        "root",
        () -> {
          Span.current().setAttribute("implicitName", "willbegone");
          Span.current().setAttribute("keep", "willbekept");
          new ExtractAttributesUsingAddingSpanAttributes()
              .withSpanAttributesParent("parentbegone", "parentbegone", null, "parentbegone");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("root")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("keep"), "willbekept"),
                            equalTo(stringKey("implicitName"), "foo"),
                            equalTo(stringKey("explicitName"), "bar"))));
  }
}
