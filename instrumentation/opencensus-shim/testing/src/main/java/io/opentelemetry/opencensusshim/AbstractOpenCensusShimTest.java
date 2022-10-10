/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opencensusshim;

import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Tracing;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;

public abstract class AbstractOpenCensusShimTest {

  protected abstract InstrumentationExtension testing();

  @Test
  void testCrossOtelOcBoundary() {
    Tracer tracer = testing().getOpenTelemetry().getTracer("opencensus-shim", "0.0.0");
    Span span = tracer.spanBuilder("test-span").setSpanKind(SpanKind.INTERNAL).startSpan();
    Scope scope = span.makeCurrent();
    try {
      io.opencensus.trace.Tracer ocTracer = Tracing.getTracer();
      io.opencensus.trace.Span internal = ocTracer.spanBuilder("internal").startSpan();
      io.opencensus.common.Scope ocScope = ocTracer.withSpan(internal);
      try {
        ocTracer
            .getCurrentSpan()
            .putAttribute("internal-only", AttributeValue.booleanAttributeValue(true));
      } finally {
        ocScope.close();
      }
      internal.end();
    } finally {
      scope.close();
    }
    span.end();

    testing()
        .waitAndAssertTraces(
            traceAssert ->
                traceAssert.hasSpansSatisfyingExactly(
                    spanAssert -> spanAssert.hasName("test-span").hasNoParent(),
                    spanAssert ->
                        spanAssert
                            .hasName("internal")
                            .hasParentSpanId(span.getSpanContext().getSpanId())
                            .hasAttributes(
                                Attributes.of(AttributeKey.booleanKey("internal-only"), true))));
  }
}
