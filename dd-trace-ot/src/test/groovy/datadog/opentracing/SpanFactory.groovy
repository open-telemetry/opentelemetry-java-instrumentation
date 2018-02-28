package datadog.opentracing

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
      new TraceCollection(tracer, 1L),
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
      new TraceCollection(tracer, 1L),
      tracer)
    return new DDSpan(1, context)
  }

  static newSpanOf(TraceCollection trace) {
    def context = new DDSpanContext(
      trace.traceId,
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
      trace,
      trace.tracer)
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
      new TraceCollection(tracer, 1L),
      tracer)
    context.setTag("env", envName)
    return new DDSpan(0l, context)
  }
}
