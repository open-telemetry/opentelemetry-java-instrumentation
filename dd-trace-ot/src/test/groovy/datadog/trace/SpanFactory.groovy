package datadog.trace

import datadog.opentracing.DDSpan
import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.trace.common.sampling.PrioritySampling

class SpanFactory {
  static newSpanOf(long timestampMicro) {
    def context = new DDSpanContext(
      1L,
      1L,
      0L,
      "fakeService",
      "fakeOperation",
      "fakeResource",
      PrioritySampling.UNSET,
      Collections.emptyMap(),
      false,
      "fakeType",
      Collections.emptyMap(),
      null,
      new DDTracer())
    return new DDSpan(timestampMicro, context)
  }

  static newSpanOf(DDTracer tracer) {
    def context = new DDSpanContext(
      1L,
      1L,
      0L,
      "fakeService",
      "fakeOperation",
      "fakeResource",
      PrioritySampling.UNSET,
      Collections.emptyMap(),
      false,
      "fakeType",
      Collections.emptyMap(),
      null,
      tracer)
    return new DDSpan(1, context)
  }
}
