/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test

import com.google.common.base.Predicate
import com.google.common.base.Predicates
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.OpenTelemetry
import io.opentelemetry.context.propagation.DefaultContextPropagators
import io.opentelemetry.instrumentation.test.asserts.InMemoryExporterAssert
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.propagation.HttpTraceContext
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
    // TODO this is probably temporary until default propagators are supplied by SDK
    //  https://github.com/open-telemetry/opentelemetry-java/issues/1742
    //  currently checking against no-op implementation so that it won't override aws-lambda
    //  propagator configuration
    if (OpenTelemetry.getGlobalPropagators().getTextMapPropagator().getClass().getSimpleName() == "NoopTextMapPropagator") {
      OpenTelemetry.setGlobalPropagators(DefaultContextPropagators.builder()
        .addTextMapPropagator(HttpTraceContext.getInstance())
        .build())
    }
    OpenTelemetrySdk.getGlobalTracerManagement().addSpanProcessor(TEST_WRITER)
  }

  @Before
  void beforeTest() {
    TEST_WRITER.clear()
  }

  protected void assertTraces(
    final int size,
    @ClosureParams(
      value = SimpleType,
      options = "io.opentelemetry.instrumentation.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    InMemoryExporterAssert.assertTraces(
      TEST_WRITER, size, Predicates.<List<SpanData>> alwaysFalse(), spec)
  }

  protected void assertTracesWithFilter(
    final int size,
    final Predicate<List<SpanData>> excludes,
    @ClosureParams(
      value = SimpleType,
      options = "io.opentelemetry.instrumentation.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    InMemoryExporterAssert.assertTraces(TEST_WRITER, size, excludes, spec)
  }
}
