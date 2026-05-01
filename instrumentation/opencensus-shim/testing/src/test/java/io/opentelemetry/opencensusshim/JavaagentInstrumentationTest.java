/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opencensusshim;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.samplers.Samplers;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JavaagentInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @BeforeEach
  void setup() {
    // by default in opencensus, a probability sampling is used which is not 100%;
    // we specifically set this configuration here to always sample to ensure traces are emitted for
    // our tests
    Tracing.getTraceConfig()
        .updateActiveTraceParams(
            Tracing.getTraceConfig().getActiveTraceParams().toBuilder()
                .setSampler(Samplers.alwaysSample())
                .build());
  }

  @Test
  void testInterleavedSpansOcFirst() {
    io.opencensus.trace.Tracer ocTracer = Tracing.getTracer();
    Tracer otelTracer = testing.getOpenTelemetry().getTracer("test");
    cleanup.deferCleanup(() -> Tracing.getExportComponent().shutdown());

    io.opencensus.trace.Span outerSpan = ocTracer.spanBuilder("outer-span").startSpan();
    Span midSpan;
    io.opencensus.trace.Span innerSpan;

    outerSpan.putAttribute("outer", AttributeValue.booleanAttributeValue(true));

    try (io.opencensus.common.Scope outerScope = ocTracer.withSpan(outerSpan)) {
      midSpan =
          otelTracer
              .spanBuilder("mid-span")
              .setSpanKind(SpanKind.INTERNAL)
              .setAttribute("middle", true)
              .startSpan();
      try (Scope midScope = midSpan.makeCurrent()) {
        innerSpan = ocTracer.spanBuilder("inner-span").startSpan();
        innerSpan.putAttribute("inner", AttributeValue.booleanAttributeValue(true));

        // make current and immediately close -- avoid empty try block
        ocTracer.withSpan(innerSpan).close();

        innerSpan.end();
      } finally {
        midSpan.end();
      }
    } finally {
      outerSpan.end();
    }

    // expecting 1 trace with 3 spans
    testing.waitAndAssertTraces(
        trace ->
            // ensure each span's attributes haven't seeped into parents or children
            trace.hasSpansSatisfyingExactly(
                // outer span
                span ->
                    span.hasName("outer-span")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanKey("outer"), true),
                            equalTo(booleanKey("inner"), null),
                            equalTo(booleanKey("middle"), null)),
                // middle span
                span ->
                    span.hasName("mid-span")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanKey("middle"), true),
                            equalTo(booleanKey("inner"), null),
                            equalTo(booleanKey("outer"), null)),
                // inner span
                span ->
                    span.hasName("inner-span")
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanKey("inner"), true),
                            equalTo(booleanKey("middle"), null),
                            equalTo(booleanKey("outer"), null))));
  }

  @Test
  void testInterleavedSpansOtelFirst() {
    io.opencensus.trace.Tracer ocTracer = Tracing.getTracer();
    Tracer otelTracer = testing.getOpenTelemetry().getTracer("test");
    cleanup.deferCleanup(() -> Tracing.getExportComponent().shutdown());

    Span outerSpan =
        otelTracer
            .spanBuilder("outer-span")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("outer", true)
            .startSpan();
    io.opencensus.trace.Span midSpan;
    Span innerSpan;

    try (Scope outerScope = outerSpan.makeCurrent()) {
      midSpan = ocTracer.spanBuilder("mid-span").startSpan();
      midSpan.putAttribute("middle", AttributeValue.booleanAttributeValue(true));
      try (io.opencensus.common.Scope midScope = ocTracer.withSpan(midSpan)) {
        innerSpan =
            otelTracer
                .spanBuilder("inner-span")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("inner", true)
                .startSpan();

        // make current and immediately close -- avoid empty try block
        innerSpan.makeCurrent().close();

        innerSpan.end();
      } finally {
        midSpan.end();
      }
    } finally {
      outerSpan.end();
    }

    // expecting 1 trace with 3 spans
    testing.waitAndAssertTraces(
        trace ->
            // ensure each span's attributes haven't seeped into parents or children
            trace.hasSpansSatisfyingExactly(
                // outer span
                span ->
                    span.hasName("outer-span")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanKey("outer"), true),
                            equalTo(booleanKey("inner"), null),
                            equalTo(booleanKey("middle"), null)),
                // middle span
                span ->
                    span.hasName("mid-span")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanKey("middle"), true),
                            equalTo(booleanKey("inner"), null),
                            equalTo(booleanKey("outer"), null)),
                // inner span
                span ->
                    span.hasName("inner-span")
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanKey("inner"), true),
                            equalTo(booleanKey("middle"), null),
                            equalTo(booleanKey("outer"), null))));
  }

  @Test
  void testStartingWithOtelSpan() {
    io.opencensus.trace.Tracer ocTracer = Tracing.getTracer();
    Tracer otelTracer = testing.getOpenTelemetry().getTracer("test");
    cleanup.deferCleanup(() -> Tracing.getExportComponent().shutdown());

    Span otelSpan =
        otelTracer
            .spanBuilder("otel-span")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("present-on-otel", true)
            .startSpan();

    io.opencensus.trace.Span ocSpan;
    try (Scope scope = otelSpan.makeCurrent()) {
      ocSpan = ocTracer.spanBuilder("oc-span").startSpan();
      try (io.opencensus.common.Scope ocScope = ocTracer.withSpan(ocSpan)) {
        ocTracer
            .getCurrentSpan()
            .putAttribute("present-on-oc", AttributeValue.booleanAttributeValue(true));
      }
      ocSpan.end();
    }
    otelSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("otel-span")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanKey("present-on-otel"), true)),
                span ->
                    span.hasName("oc-span")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanKey("present-on-oc"), true))));
  }

  @Test
  void testStartingWithOpenCensusSpan() {
    io.opencensus.trace.Tracer ocTracer = Tracing.getTracer();
    Tracer otelTracer = testing.getOpenTelemetry().getTracer("test");
    cleanup.deferCleanup(() -> Tracing.getExportComponent().shutdown());

    io.opencensus.trace.Span ocSpan = ocTracer.spanBuilder("oc-span").startSpan();

    ocSpan.putAttribute("present-on-oc", AttributeValue.booleanAttributeValue(true));

    Span otelSpan;
    try (io.opencensus.common.Scope ocScope = ocTracer.withSpan(ocSpan)) {
      otelSpan = otelTracer.spanBuilder("otel-span").setSpanKind(SpanKind.INTERNAL).startSpan();
      try (Scope scope = otelSpan.makeCurrent()) {
        Span.current().setAttribute("present-on-otel", true);
      }
      otelSpan.end();
    }
    ocSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("oc-span")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(equalTo(booleanKey("present-on-oc"), true)),
                span ->
                    span.hasName("otel-span")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanKey("present-on-otel"), true))));
  }

  @Test
  void testNestedOpenCensusSpans() {
    io.opencensus.trace.Tracer ocTracer = Tracing.getTracer();
    cleanup.deferCleanup(() -> Tracing.getExportComponent().shutdown());

    io.opencensus.trace.Span outerSpan = ocTracer.spanBuilder("outer-span").startSpan();
    io.opencensus.trace.Span midSpan;
    io.opencensus.trace.Span innerSpan;

    outerSpan.putAttribute("outer", AttributeValue.booleanAttributeValue(true));

    try (io.opencensus.common.Scope outerScope = ocTracer.withSpan(outerSpan)) {
      midSpan = ocTracer.spanBuilder("mid-span").startSpan();
      midSpan.putAttribute("middle", AttributeValue.booleanAttributeValue(true));
      try (io.opencensus.common.Scope midScope = ocTracer.withSpan(midSpan)) {
        innerSpan = ocTracer.spanBuilder("inner-span").startSpan();
        innerSpan.putAttribute("inner", AttributeValue.booleanAttributeValue(true));

        // make current and immediately close -- avoid empty try block
        ocTracer.withSpan(innerSpan).close();

        innerSpan.end();
      } finally {
        midSpan.end();
      }
    } finally {
      outerSpan.end();
    }

    // expecting 1 trace with 3 spans
    testing.waitAndAssertTraces(
        trace ->
            // ensure each span's attributes haven't seeped into parents or children
            trace.hasSpansSatisfyingExactly(
                // outer span
                span ->
                    span.hasName("outer-span")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanKey("outer"), true),
                            equalTo(booleanKey("inner"), null),
                            equalTo(booleanKey("middle"), null)),
                // middle span
                span ->
                    span.hasName("mid-span")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanKey("middle"), true),
                            equalTo(booleanKey("inner"), null),
                            equalTo(booleanKey("outer"), null)),
                // inner span
                span ->
                    span.hasName("inner-span")
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanKey("inner"), true),
                            equalTo(booleanKey("middle"), null),
                            equalTo(booleanKey("outer"), null))));
  }
}
