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

  @Shared
  def span = Mock(Span)
  @Shared
  def root = Context.root()

  def newTracer() {
    return new BaseTracer() {
      @Override
      protected String getInstrumentationName() {
        return "BaseTracerTest"
      }
    }
  }

  def "test shouldStartSpan"() {
    setup:

    when:
    boolean result = tracer.shouldStartSpan(kind, context)

    then:
    result == expected

    where:
    kind             | context                           | expected
    Span.Kind.CLIENT | root                              | true
    Span.Kind.CLIENT | tracer.withClientSpan(root, span) | false
    Span.Kind.SERVER | root                              | true
    Span.Kind.SERVER | tracer.withServerSpan(root, span) | false
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
