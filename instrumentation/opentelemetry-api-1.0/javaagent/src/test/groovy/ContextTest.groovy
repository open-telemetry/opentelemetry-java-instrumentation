/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

class ContextTest extends AgentInstrumentationSpecification {

  def "Span.current() should return invalid"() {
    when:
    def span = Span.current()

    then:
    !span.spanContext.valid
  }

  def "Span.current() should return span"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def scope = testSpan.makeCurrent()
    def span = Span.current()
    scope.close()

    then:
    span == testSpan
  }

  def "Span.fromContext should return invalid"() {
    when:
    def span = Span.fromContext(Context.current())

    then:
    !span.spanContext.valid
  }

  def "getSpan should return span"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def scope = testSpan.makeCurrent()
    def span = Span.fromContext(Context.current())
    scope.close()

    then:
    span == testSpan
  }

  def "Span.fromContextOrNull should return null"() {
    when:
    def span = Span.fromContextOrNull(Context.current())

    then:
    span == null
  }

  def "Span.fromContextOrNull should return span"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def scope = testSpan.makeCurrent()
    def span = Span.fromContextOrNull(Context.current())
    scope.close()

    then:
    span == testSpan
  }
}
