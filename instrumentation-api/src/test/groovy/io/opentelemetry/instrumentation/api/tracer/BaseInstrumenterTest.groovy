/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer


import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter
import spock.lang.Shared
import spock.lang.Specification

// TODO add tests for BaseTracer
class BaseInstrumenterTest extends Specification {
  @Shared
  def tracer = newTracer()

  @Shared
  def resolvedAddress = new InetSocketAddress("github.com", 999)

  def span = Mock(Span)

  def newTracer() {
    return new BaseInstrumenter() {
      @Override
      protected String getInstrumentationName() {
        return "BaseTracerTest"
      }
    }
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
