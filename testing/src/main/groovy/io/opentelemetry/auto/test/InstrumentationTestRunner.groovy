/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
