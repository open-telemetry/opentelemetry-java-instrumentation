package io.opentelemetry.auto.test

import com.google.common.base.Predicate
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.auto.test.asserts.InMemoryExporterAssert
import io.opentelemetry.sdk.trace.data.SpanData
import spock.lang.Specification

abstract class InstrumentationSpecification extends Specification {
  abstract void assertTraces(
      final int size,
      @ClosureParams(
          value = SimpleType.class,
          options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
      @DelegatesTo(value = InMemoryExporterAssert.class, strategy = Closure.DELEGATE_FIRST)
      final Closure spec)

  abstract void assertTracesWithFilter(
      final int size,
      final Predicate<List<SpanData>> excludes,
      @ClosureParams(
          value = SimpleType.class,
          options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
      @DelegatesTo(value = InMemoryExporterAssert.class, strategy = Closure.DELEGATE_FIRST)
      final Closure spec)
}
