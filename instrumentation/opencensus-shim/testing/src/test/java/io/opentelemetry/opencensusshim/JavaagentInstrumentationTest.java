/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opencensusshim;

import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Tracing;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import org.assertj.core.api.AbstractBooleanAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JavaagentInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testInterleavedSpansOcFirst() {
    io.opencensus.trace.Tracer ocTracer = Tracing.getTracer();
    Tracer otelTracer = testing.getOpenTelemetry().getTracer("test");

    io.opencensus.trace.Span outerSpan = findSampledOCSpan(ocTracer, "outer-span");
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
        innerSpan = findSampledOCSpan(ocTracer, "inner-span");
        innerSpan.putAttribute("inner", AttributeValue.booleanAttributeValue(true));
        try (io.opencensus.common.Scope innerScope = ocTracer.withSpan(innerSpan)) {
        } finally {
          innerSpan.end();
        }
      } finally {
        midSpan.end();
      }
    } finally {
      outerSpan.end();
    }

    Tracing.getExportComponent().shutdown();

    SpanContext outerContext = SpanConverterProxy.mapSpanContext(outerSpan.getContext());

    // expecting 1 trace with 3 spans
    testing.waitAndAssertTraces(
        ta ->
            // ensure each span's attributes haven't seeped into parents or children
            ta.hasSpansSatisfyingExactly(
                // inner span
                sa ->
                    sa.hasName("inner-span")
                        .hasParentSpanId(midSpan.getSpanContext().getSpanId())
                        .hasAttribute(AttributeKey.booleanKey("inner"), true)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("middle"), AbstractBooleanAssert::isNull),
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("outer"), AbstractBooleanAssert::isNull)),
                // middle span
                sa ->
                    sa.hasName("mid-span")
                        .hasParentSpanId(outerContext.getSpanId())
                        .hasAttribute(AttributeKey.booleanKey("middle"), true)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("inner"), AbstractBooleanAssert::isNull),
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("outer"), AbstractBooleanAssert::isNull)),
                // outer span
                sa ->
                    sa.hasName("outer-span")
                        .hasNoParent()
                        .hasAttribute(AttributeKey.booleanKey("outer"), true)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("inner"), AbstractBooleanAssert::isNull),
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("middle"),
                                AbstractBooleanAssert::isNull))));
  }

  @Test
  void testInterleavedSpansOtelFirst() {
    io.opencensus.trace.Tracer ocTracer = Tracing.getTracer();
    Tracer otelTracer = testing.getOpenTelemetry().getTracer("test");

    Span outerSpan =
        otelTracer
            .spanBuilder("outer-span")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("outer", true)
            .startSpan();
    io.opencensus.trace.Span midSpan;
    Span innerSpan;

    try (Scope outerScope = outerSpan.makeCurrent()) {
      midSpan = findSampledOCSpan(ocTracer, "mid-span");
      midSpan.putAttribute("middle", AttributeValue.booleanAttributeValue(true));
      try (io.opencensus.common.Scope midScope = ocTracer.withSpan(midSpan)) {
        innerSpan =
            otelTracer
                .spanBuilder("inner-span")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("inner", true)
                .startSpan();
        try (Scope innerScope = innerSpan.makeCurrent()) {
        } finally {
          innerSpan.end();
        }
      } finally {
        midSpan.end();
      }
    } finally {
      outerSpan.end();
    }

    Tracing.getExportComponent().shutdown();

    SpanContext midContext = SpanConverterProxy.mapSpanContext(midSpan.getContext());

    // expecting 1 trace with 3 spans
    testing.waitAndAssertTraces(
        ta ->
            // ensure each span's attributes haven't seeped into parents or children
            ta.hasSpansSatisfyingExactly(
                // inner span
                sa ->
                    sa.hasName("inner-span")
                        .hasParentSpanId(midContext.getSpanId())
                        .hasAttribute(AttributeKey.booleanKey("inner"), true)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("middle"), AbstractBooleanAssert::isNull),
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("outer"), AbstractBooleanAssert::isNull)),
                // middle span
                sa ->
                    sa.hasName("mid-span")
                        .hasParentSpanId(outerSpan.getSpanContext().getSpanId())
                        .hasAttribute(AttributeKey.booleanKey("middle"), true)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("inner"), AbstractBooleanAssert::isNull),
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("outer"), AbstractBooleanAssert::isNull)),
                // outer span
                sa ->
                    sa.hasName("outer-span")
                        .hasNoParent()
                        .hasAttribute(AttributeKey.booleanKey("outer"), true)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("inner"), AbstractBooleanAssert::isNull),
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("middle"),
                                AbstractBooleanAssert::isNull))));
  }

  @Test
  void testStartingWithOtelSpan() {
    io.opencensus.trace.Tracer ocTracer = Tracing.getTracer();
    Tracer otelTracer = testing.getOpenTelemetry().getTracer("test");

    Span otelSpan =
        otelTracer
            .spanBuilder("otel-span")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("present-on-otel", true)
            .startSpan();

    io.opencensus.trace.Span ocSpan;
    try (Scope scope = otelSpan.makeCurrent()) {
      ocSpan = findSampledOCSpan(ocTracer, "oc-span");
      try (io.opencensus.common.Scope ocScope = ocTracer.withSpan(ocSpan)) {
        ocTracer
            .getCurrentSpan()
            .putAttribute("present-on-oc", AttributeValue.booleanAttributeValue(true));
      }
      ocSpan.end();
    }
    otelSpan.end();

    Tracing.getExportComponent().shutdown();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("oc-span")
                        .hasParentSpanId(otelSpan.getSpanContext().getSpanId())
                        .hasAttribute(AttributeKey.booleanKey("present-on-oc"), true),
                span ->
                    span.hasName("otel-span")
                        .hasNoParent()
                        .hasAttribute(AttributeKey.booleanKey("present-on-otel"), true)));
  }

  @Test
  void testStartingWithOpenCensusSpan() {
    io.opencensus.trace.Tracer ocTracer = Tracing.getTracer();
    Tracer otelTracer = testing.getOpenTelemetry().getTracer("test");

    io.opencensus.trace.Span ocSpan = findSampledOCSpan(ocTracer, "oc-span");

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

    Tracing.getExportComponent().shutdown();

    SpanContext ocContext = SpanConverterProxy.mapSpanContext(ocSpan.getContext());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("otel-span")
                        .hasParentSpanId(ocContext.getSpanId())
                        .hasAttribute(AttributeKey.booleanKey("present-on-otel"), true),
                span ->
                    span.hasName("oc-span")
                        .hasNoParent()
                        .hasAttribute(AttributeKey.booleanKey("present-on-oc"), true)));
  }

  @Test
  void testNestedOpenCensusSpans() {
    io.opencensus.trace.Tracer ocTracer = Tracing.getTracer();

    io.opencensus.trace.Span outerSpan = findSampledOCSpan(ocTracer, "outer-span");
    io.opencensus.trace.Span midSpan;
    io.opencensus.trace.Span innerSpan;

    outerSpan.putAttribute("outer", AttributeValue.booleanAttributeValue(true));

    try (io.opencensus.common.Scope outerScope = ocTracer.withSpan(outerSpan)) {
      midSpan = findSampledOCSpan(ocTracer, "mid-span");
      midSpan.putAttribute("middle", AttributeValue.booleanAttributeValue(true));
      try (io.opencensus.common.Scope midScope = ocTracer.withSpan(midSpan)) {
        innerSpan = findSampledOCSpan(ocTracer, "inner-span");
        innerSpan.putAttribute("inner", AttributeValue.booleanAttributeValue(true));
        try (io.opencensus.common.Scope innerScope = ocTracer.withSpan(innerSpan)) {
        } finally {
          innerSpan.end();
        }
      } finally {
        midSpan.end();
      }
    } finally {
      outerSpan.end();
    }

    Tracing.getExportComponent().shutdown();

    SpanContext outerContext = SpanConverterProxy.mapSpanContext(outerSpan.getContext());
    SpanContext midContext = SpanConverterProxy.mapSpanContext(midSpan.getContext());

    // expecting 1 trace with 3 spans
    testing.waitAndAssertTraces(
        ta ->
            // ensure each span's attributes haven't seeped into parents or children
            ta.hasSpansSatisfyingExactly(
                // inner span
                sa ->
                    sa.hasName("inner-span")
                        .hasParentSpanId(midContext.getSpanId())
                        .hasAttribute(AttributeKey.booleanKey("inner"), true)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("middle"), AbstractBooleanAssert::isNull),
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("outer"), AbstractBooleanAssert::isNull)),
                // middle span
                sa ->
                    sa.hasName("mid-span")
                        .hasParentSpanId(outerContext.getSpanId())
                        .hasAttribute(AttributeKey.booleanKey("middle"), true)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("inner"), AbstractBooleanAssert::isNull),
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("outer"), AbstractBooleanAssert::isNull)),
                // outer span
                sa ->
                    sa.hasName("outer-span")
                        .hasNoParent()
                        .hasAttribute(AttributeKey.booleanKey("outer"), true)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("inner"), AbstractBooleanAssert::isNull),
                            OpenTelemetryAssertions.satisfies(
                                AttributeKey.booleanKey("middle"),
                                AbstractBooleanAssert::isNull))));
  }

  static io.opencensus.trace.Span findSampledOCSpan(
      io.opencensus.trace.Tracer ocTracer, String name) {
    // loop until we get a span which will be exported (isSampled);
    // opencensus-shim at its root uses the default sampler which is probability-based,
    // and we need the span to be emitted in order to adequately test;
    //
    // todo otel spans should correctly inherit the sampling state of a parent OC span;
    //  attow, the otel span below does not respect the isSampled state of the OC span and is
    //  _always_ exported
    io.opencensus.trace.Span ocSpan;
    do {
      ocSpan = ocTracer.spanBuilder(name).startSpan();
    } while (!ocSpan.getContext().getTraceOptions().isSampled());
    return ocSpan;
  }
}
