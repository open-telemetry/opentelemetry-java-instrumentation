package io.opentelemetry.auto.test

import com.google.common.base.Predicate
import com.google.common.base.Predicates
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.auto.test.asserts.InMemoryExporterAssert
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.Before
import spock.lang.Specification
/**
 * A spock test runner which automatically initializes an in-memory exporter that can be used to
 * verify traces.
 */
abstract class InstrumentationTestRunner extends Specification {

  protected static final InMemoryExporter TEST_WRITER

  static {
    TEST_WRITER = new InMemoryExporter()
    OpenTelemetrySdk.getTracerProvider().addSpanProcessor(TEST_WRITER)
  }

  @Before
  void beforeTest() {
    TEST_WRITER.clear()
  }

  protected void assertTraces(
      final int size,
      @ClosureParams(
          value = SimpleType,
          options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
      @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
      final Closure spec) {
    InMemoryExporterAssert.assertTraces(
        TEST_WRITER, size, Predicates.<List<SpanData>>alwaysFalse(), spec)
  }

  protected void assertTracesWithFilter(
      final int size,
      final Predicate<List<SpanData>> excludes,
      @ClosureParams(
          value = SimpleType,
          options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
      @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
      final Closure spec) {
    InMemoryExporterAssert.assertTraces(TEST_WRITER, size, excludes, spec)
  }
}
