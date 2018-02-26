package datadog.trace

import datadog.opentracing.DDSpan
import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.TraceCollection
import datadog.trace.common.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter

class SpanFactory {
  static newSpanOf(long timestampMicro) {
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
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
      new TraceCollection(tracer),
      tracer)
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
      new TraceCollection(tracer),
      tracer)
    return new DDSpan(1, context)
  }

  static DDSpan newSpanOf(String serviceName, String envName) {
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    def context = new DDSpanContext(
      1L,
      1L,
      0L,
      serviceName,
      "fakeOperation",
      "fakeResource",
      PrioritySampling.UNSET,
      Collections.emptyMap(),
      false,
      "fakeType",
      Collections.emptyMap(),
      new TraceCollection(tracer),
      tracer)
    context.setTag("env", envName)
    return new DDSpan(0l, context)
  }
}
