/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import spock.lang.Shared
import spock.lang.Specification

// TODO add tests for BaseTracer
class BaseTracerTest extends Specification {
  @Shared
  def tracer = newTracer()

  @Shared
  def resolvedAddress = new InetSocketAddress("github.com", 999)

  def span = Mock(Span)

  @Shared
  def root = Context.root()

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
    boolean result = tracer.shouldStartSpan(kind, context)

    then:
    result == expected

    where:
    kind               | context                                   | expected
    Span.Kind.CLIENT   | root                                      | true
    Span.Kind.SERVER   | root                                      | true
    Span.Kind.INTERNAL | root                                      | true
    Span.Kind.PRODUCER | root                                      | true
    Span.Kind.CONSUMER | root                                      | true
    Span.Kind.CLIENT   | tracer.withClientSpan(root, existingSpan) | false
    Span.Kind.SERVER   | tracer.withClientSpan(root, existingSpan) | true
    Span.Kind.INTERNAL | tracer.withClientSpan(root, existingSpan) | true
    Span.Kind.CONSUMER | tracer.withClientSpan(root, existingSpan) | true
    Span.Kind.PRODUCER | tracer.withClientSpan(root, existingSpan) | true
    Span.Kind.SERVER   | tracer.withServerSpan(root, existingSpan) | false
    Span.Kind.INTERNAL | tracer.withServerSpan(root, existingSpan) | true
    Span.Kind.CONSUMER | tracer.withServerSpan(root, existingSpan) | true
    Span.Kind.PRODUCER | tracer.withServerSpan(root, existingSpan) | true
    Span.Kind.CLIENT   | tracer.withServerSpan(root, existingSpan) | true
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
