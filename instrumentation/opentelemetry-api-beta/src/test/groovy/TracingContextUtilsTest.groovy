/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static application.io.opentelemetry.trace.TracingContextUtils.currentContextWith
import static application.io.opentelemetry.trace.TracingContextUtils.getCurrentSpan
import static application.io.opentelemetry.trace.TracingContextUtils.getSpan
import static application.io.opentelemetry.trace.TracingContextUtils.getSpanWithoutDefault

import application.io.grpc.Context
import application.io.opentelemetry.OpenTelemetry
import io.opentelemetry.instrumentation.test.AgentTestRunner

class TracingContextUtilsTest extends AgentTestRunner {

  def "getCurrentSpan should return invalid"() {
    when:
    def span = getCurrentSpan()

    then:
    !span.context.valid
  }

  def "getCurrentSpan should return span"() {
    when:
    def tracer = OpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def scope = currentContextWith(testSpan)
    def span = getCurrentSpan()
    scope.close()

    then:
    span == testSpan
  }

  def "getSpan should return invalid"() {
    when:
    def span = getSpan(Context.current())

    then:
    !span.context.valid
  }

  def "getSpan should return span"() {
    when:
    def tracer = OpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def scope = currentContextWith(testSpan)
    def span = getSpan(Context.current())
    scope.close()

    then:
    span == testSpan
  }

  def "getSpanWithoutDefault should return null"() {
    when:
    def span = getSpanWithoutDefault(Context.current())

    then:
    span == null
  }

  def "getSpanWithoutDefault should return span"() {
    when:
    def tracer = OpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def scope = currentContextWith(testSpan)
    def span = getSpanWithoutDefault(Context.current())
    scope.close()

    then:
    span == testSpan
  }
}
