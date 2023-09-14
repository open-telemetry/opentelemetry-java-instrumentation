/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AddingSpanAttributesInstrumentationTest {

  @RegisterExtension
  public static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @Test
  void captureAttributesInNewSpan() throws Exception {

    testing.runWithSpan(
        "root",
        () ->
            new ExtractAttributesUsingAddingSpanAttributes()
                .withSpanTakesPrecedence("foo", "bar", null, "baz"));

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("root")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid()),
                        span ->
                            assertThat(span)
                                .hasName(
                                    "ExtractAttributesUsingAddingSpanAttributes.withSpanTakesPrecedence")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(trace.get(0).getSpanId())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    SemanticAttributes.CODE_NAMESPACE,
                                                    ExtractAttributesUsingAddingSpanAttributes.class
                                                        .getName()),
                                                entry(
                                                    SemanticAttributes.CODE_FUNCTION,
                                                    "withSpanTakesPrecedence"),
                                                entry(
                                                    AttributeKey.stringKey("implicitName"), "foo"),
                                                entry(
                                                    AttributeKey.stringKey("explicitName"),
                                                    "bar")))));
  }

  @Test
  void captureAttributesInCurrentSpan() throws Exception {

    testing.runWithSpan(
        "root",
        () ->
            new ExtractAttributesUsingAddingSpanAttributes()
                .withSpanAttributes("foo", "bar", null, "baz"));

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("root")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(
                                                    AttributeKey.stringKey("implicitName"), "foo"),
                                                entry(
                                                    AttributeKey.stringKey("explicitName"),
                                                    "bar")))));
  }

  @Test
  void noExistingSpan() throws Exception {

    new ExtractAttributesUsingAddingSpanAttributes().withSpanAttributes("foo", "bar", null, "baz");

    assertThat(testing.waitForTraces(0));
  }

  @Test
  void overwriteAttributes() throws Exception {

    testing.runWithSpan(
        "root",
        () -> {
          Span.current().setAttribute("implicitName", "willbegone");
          Span.current().setAttribute("keep", "willbekept");
          new ExtractAttributesUsingAddingSpanAttributes()
              .withSpanAttributes("foo", "bar", null, "baz");
        });

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("root")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(AttributeKey.stringKey("keep"), "willbekept"),
                                                entry(
                                                    AttributeKey.stringKey("implicitName"), "foo"),
                                                entry(
                                                    AttributeKey.stringKey("explicitName"),
                                                    "bar")))));
  }

  @Test
  void multiMethodOverwriteAttributes() throws Exception {

    testing.runWithSpan(
        "root",
        () -> {
          Span.current().setAttribute("implicitName", "willbegone");
          Span.current().setAttribute("keep", "willbekept");
          new ExtractAttributesUsingAddingSpanAttributes()
              .withSpanAttributesParent("parentbegone", "parentbegone", null, "parentbegone");
        });

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("root")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(AttributeKey.stringKey("keep"), "willbekept"),
                                                entry(
                                                    AttributeKey.stringKey("implicitName"), "foo"),
                                                entry(
                                                    AttributeKey.stringKey("explicitName"),
                                                    "bar")))));
  }
}
