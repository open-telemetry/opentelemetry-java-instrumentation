package io.opentelemetry.opencensusshim;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Tracing;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;


public class JavaagentInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @org.junit.jupiter.api.Test
  void test() {
    Tracer tracer = testing.getOpenTelemetry().getTracer("test");
    Span span = tracer.spanBuilder("otel-span").setSpanKind(SpanKind.INTERNAL).startSpan();
    Scope scope = span.makeCurrent();
    try {
      io.opencensus.trace.Tracer ocTracer = Tracing.getTracer();
      io.opencensus.trace.Span internal = ocTracer.spanBuilder("oc-span").startSpan();
      io.opencensus.common.Scope ocScope = ocTracer.withSpan(internal);
      try {
        ocTracer
            .getCurrentSpan()
            .putAttribute("present-on-oc", AttributeValue.booleanAttributeValue(true));
      } finally {
        ocScope.close();
      }
      internal.end();
    } finally {
      scope.close();
    }
    span.end();

    // will pass when the opencensus shim is correctly proxying calls to the ApplicationSpanImpl instance emitted by the javaagent-configured GlobalOpenTelemetry Tracer
    testing
        .waitAndAssertTraces(
            traceAssert ->
                traceAssert.hasSpansSatisfyingExactly(
                    spanAssert -> spanAssert.hasName("otel-span").hasNoParent(),
                    spanAssert ->
                        spanAssert
                            .hasName("oc-span")
                            .hasParentSpanId(span.getSpanContext().getSpanId())
                            .hasAttributesSatisfyingExactly(
                                equalTo(AttributeKey.booleanKey("present-on-oc"), true))));
  }
}
