/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test

import com.google.common.base.Predicate
import com.google.common.base.Predicates
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.propagation.HttpTraceContext
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.DefaultContextPropagators
import io.opentelemetry.instrumentation.test.asserts.InMemoryExporterAssert
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.Before
import spock.lang.Specification

/**
 * A spock test runner which automatically initializes an in-memory exporter that can be used to
 * verify traces.
 */
abstract class InstrumentationTestRunner extends Specification {

  protected static final InMemorySpanExporter testExporter

  private static boolean forceFlushCalled

  static {
    testExporter = InMemorySpanExporter.create()
    // TODO this is probably temporary until default propagators are supplied by SDK
    //  https://github.com/open-telemetry/opentelemetry-java/issues/1742
    //  currently checking against no-op implementation so that it won't override aws-lambda
    //  propagator configuration
    if (OpenTelemetry.getGlobalPropagators().getTextMapPropagator().getClass().getSimpleName() == "NoopTextMapPropagator") {
      OpenTelemetry.setGlobalPropagators(DefaultContextPropagators.builder()
        .addTextMapPropagator(HttpTraceContext.getInstance())
        .build())
    }
    def delegate = SimpleSpanProcessor.builder(testExporter).build()
    OpenTelemetrySdk.getGlobalTracerManagement()
      .addSpanProcessor(new SpanProcessor() {
        @Override
        void onStart(Context parentContext, ReadWriteSpan span) {
          delegate.onStart(parentContext, span)
        }

        @Override
        boolean isStartRequired() {
          return delegate.isStartRequired()
        }

        @Override
        void onEnd(ReadableSpan span) {
          delegate.onEnd(span)
        }

        @Override
        boolean isEndRequired() {
          return delegate.isEndRequired()
        }

        @Override
        CompletableResultCode shutdown() {
          return delegate.shutdown()
        }

        @Override
        CompletableResultCode forceFlush() {
          forceFlushCalled = true
          return delegate.forceFlush()
        }
      })
  }

  @Before
  void beforeTest() {
    testExporter.reset()
    forceFlushCalled = false
  }

  protected boolean forceFlushCalled() {
    return forceFlushCalled
  }

  protected void assertTraces(
    final int size,
    @ClosureParams(
      value = SimpleType,
      options = "io.opentelemetry.instrumentation.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    InMemoryExporterAssert.assertTraces(
      getTraces(), size, Predicates.<List<SpanData>> alwaysFalse(), spec)
  }

  protected void assertTracesWithFilter(
    final int size,
    final Predicate<List<SpanData>> excludes,
    @ClosureParams(
      value = SimpleType,
      options = "io.opentelemetry.instrumentation.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    InMemoryExporterAssert.assertTraces(getTraces(), size, excludes, spec)
  }

  private static List<List<SpanData>> getTraces() {
    return InMemoryExporter.groupTraces(testExporter.getFinishedSpanItems())
  }
}
