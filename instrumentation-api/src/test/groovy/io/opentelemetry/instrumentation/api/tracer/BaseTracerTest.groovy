/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import spock.lang.Shared
import spock.lang.Specification

import static org.junit.Assume.assumeTrue

// TODO add tests for BaseTracer
class BaseTracerTest extends Specification {

  @Shared
  def tracer = newTracer()

  def span = Mock(Span)

  @Shared
  def root = Context.root()

  @Shared
  def ContextKey<Boolean> suppressContextKey = ContextKey.named("otel.suppress_instrumentation")


  @Shared
  def existingSpan = Span.getInvalid()

  def newTracer() {
    return new BaseTracer() {
      @Override
      protected String getInstrumentationName() {
        return "BaseTracerTest"
      }
    }
  }

  def "test shouldStartSpan"() {
    when:
    boolean result = tracer.shouldStartSpan(context, kind)

    then:
    result == expected

    where:
    kind              | context                                   | expected
    SpanKind.CLIENT   | root                                      | true
    SpanKind.SERVER   | root                                      | true
    SpanKind.INTERNAL | root                                      | true
    SpanKind.PRODUCER | root                                      | true
    SpanKind.CONSUMER | root                                      | true
    SpanKind.CLIENT   | tracer.withClientSpan(root, existingSpan) | false
    SpanKind.SERVER   | tracer.withClientSpan(root, existingSpan) | true
    SpanKind.INTERNAL | tracer.withClientSpan(root, existingSpan) | true
    SpanKind.CONSUMER | tracer.withClientSpan(root, existingSpan) | true
    SpanKind.PRODUCER | tracer.withClientSpan(root, existingSpan) | true
    SpanKind.SERVER   | tracer.withServerSpan(root, existingSpan) | false
    SpanKind.INTERNAL | tracer.withServerSpan(root, existingSpan) | true
    SpanKind.CONSUMER | tracer.withServerSpan(root, existingSpan) | false
    SpanKind.PRODUCER | tracer.withServerSpan(root, existingSpan) | true
    SpanKind.CLIENT   | tracer.withServerSpan(root, existingSpan) | true
  }

  def "test shouldStartSpan is false when instrumentation is suppressed"() {
    when:
    boolean result = tracer.shouldStartSpan(context, kind)

    then:
    result == expected

    where:
    kind              | context                                                   | expected
    SpanKind.CLIENT   | root.with(BaseTracer.SUPPRESS_INSTRUMENTATION_KEY, false) | true
    SpanKind.SERVER   | root.with(BaseTracer.SUPPRESS_INSTRUMENTATION_KEY, false) | true
    SpanKind.INTERNAL | root.with(BaseTracer.SUPPRESS_INSTRUMENTATION_KEY, false) | true
    SpanKind.CONSUMER | root.with(BaseTracer.SUPPRESS_INSTRUMENTATION_KEY, false) | true
    SpanKind.PRODUCER | root.with(BaseTracer.SUPPRESS_INSTRUMENTATION_KEY, false) | true
    SpanKind.CLIENT   | root.with(BaseTracer.SUPPRESS_INSTRUMENTATION_KEY, true)  | false
    SpanKind.SERVER   | root.with(BaseTracer.SUPPRESS_INSTRUMENTATION_KEY, true)  | false
    SpanKind.INTERNAL | root.with(BaseTracer.SUPPRESS_INSTRUMENTATION_KEY, true)  | false
    SpanKind.CONSUMER | root.with(BaseTracer.SUPPRESS_INSTRUMENTATION_KEY, true)  | false
    SpanKind.PRODUCER | root.with(BaseTracer.SUPPRESS_INSTRUMENTATION_KEY, true)  | false
  }

  class SomeInnerClass implements Runnable {
    void run() {
    }
  }

  static class SomeNestedClass implements Runnable {
    void run() {
    }
  }
}
