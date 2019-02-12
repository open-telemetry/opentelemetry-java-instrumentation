package datadog.opentracing

import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter

class SpanFactory {
  static newSpanOf(long timestampMicro, String threadName = Thread.currentThread().name) {
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    def currentThreadName = Thread.currentThread().getName()
    Thread.currentThread().setName(threadName)
    def context = new DDSpanContext(
      "1",
      "1",
      "0",
      "fakeService",
      "fakeOperation",
      "fakeResource",
      PrioritySampling.UNSET,
      null,
      Collections.emptyMap(),
      false,
      "fakeType",
      Collections.emptyMap(),
      new PendingTrace(tracer, "1", [:]),
      tracer)
    Thread.currentThread().setName(currentThreadName)
    return new DDSpan(timestampMicro, context)
  }

  static newSpanOf(DDTracer tracer) {
    def context = new DDSpanContext(
      "1",
      "1",
      "0",
      "fakeService",
      "fakeOperation",
      "fakeResource",
      PrioritySampling.UNSET,
      null,
      Collections.emptyMap(),
      false,
      "fakeType",
      Collections.emptyMap(),
      new PendingTrace(tracer, "1", [:]),
      tracer)
    return new DDSpan(1, context)
  }

  static newSpanOf(PendingTrace trace) {
    def context = new DDSpanContext(
      trace.traceId,
      "1",
      "0",
      "fakeService",
      "fakeOperation",
      "fakeResource",
      PrioritySampling.UNSET,
      null,
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
      "1",
      "1",
      "0",
      serviceName,
      "fakeOperation",
      "fakeResource",
      PrioritySampling.UNSET,
      null,
      Collections.emptyMap(),
      false,
      "fakeType",
      Collections.emptyMap(),
      new PendingTrace(tracer, "1", [:]),
      tracer)
    context.setTag("env", envName)
    return new DDSpan(0l, context)
  }
}
