/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import application.io.opentelemetry.OpenTelemetry
import application.io.opentelemetry.context.Context
import application.io.opentelemetry.trace.Span
import io.opentelemetry.instrumentation.test.AgentTestRunner

class TracingContextUtilsTest extends AgentTestRunner {

  def "getCurrentSpan should return invalid"() {
    when:
    def span = Span.current()

    then:
    !span.spanContext.valid
  }

  def "getCurrentSpan should return span"() {
    when:
    def tracer = OpenTelemetry.getGlobalTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def scope = testSpan.makeCurrent()
    def span = Span.current()
    scope.close()

    then:
    span == testSpan
  }

  def "getSpan should return invalid"() {
    when:
    def span = Span.fromContext(Context.current())

    then:
    !span.spanContext.valid
  }

  def "getSpan should return span"() {
    when:
    def tracer = OpenTelemetry.getGlobalTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def scope = testSpan.makeCurrent()
    def span = Span.fromContext(Context.current())
    scope.close()

    then:
    span == testSpan
  }

  def "getSpanWithoutDefault should return null"() {
    when:
    def span = Span.fromContextOrNull(Context.current())

    then:
    span == null
  }

  def "getSpanWithoutDefault should return span"() {
    when:
    def tracer = OpenTelemetry.getGlobalTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def scope = testSpan.makeCurrent()
    def span = Span.fromContextOrNull(Context.current())
    scope.close()

    then:
    span == testSpan
  }
}
