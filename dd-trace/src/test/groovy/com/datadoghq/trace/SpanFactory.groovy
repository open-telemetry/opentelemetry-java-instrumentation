package com.datadoghq.trace

class SpanFactory {
  static newSpanOf(long timestampMicro) {
    def context = new DDSpanContext(
      1L,
      1L,
      0L,
      "fakeService",
      "fakeOperation",
      "fakeResource",
      Collections.emptyMap(),
      false,
      "fakeType",
      Collections.emptyMap(),
      null,
      null)
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
      Collections.emptyMap(),
      false,
      "fakeType",
      Collections.emptyMap(),
      null,
      tracer)
    return new DDSpan(1, context)
  }
}
