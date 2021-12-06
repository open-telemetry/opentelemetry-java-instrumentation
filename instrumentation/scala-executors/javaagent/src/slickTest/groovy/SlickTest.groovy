/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import static io.opentelemetry.api.trace.SpanKind.CLIENT

class SlickTest extends AgentInstrumentationSpecification {

  // Can't be @Shared, otherwise the work queue is initialized before the instrumentation is applied
  def database = new SlickUtils()

  def "Basic statement generates spans"() {
    setup:
    def future = database.startQuery(SlickUtils.TestQuery())
    def result = database.getResults(future)

    expect:
    result == SlickUtils.TestValue()

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "run query"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "SELECT ${SlickUtils.Db()}"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "h2"
            "$SemanticAttributes.DB_NAME" SlickUtils.Db()
            "$SemanticAttributes.DB_USER" SlickUtils.Username()
            "$SemanticAttributes.DB_CONNECTION_STRING" "h2:mem:"
            "$SemanticAttributes.DB_STATEMENT" "SELECT ?"
            "$SemanticAttributes.DB_OPERATION" "SELECT"
          }
        }
      }
    }
  }

  def "Concurrent requests do not throw exception"() {
    setup:
    def sleepFuture = database.startQuery(SlickUtils.SleepQuery())

    def future = database.startQuery(SlickUtils.TestQuery())
    def result = database.getResults(future)

    database.getResults(sleepFuture)

    expect:
    result == SlickUtils.TestValue()

    // Expect two traces because two queries have been run
    assertTraces(2) {
      trace(0, 2, {
        span(0) {}
        span(1) { kind CLIENT }
      })
      trace(1, 2, {
        span(0) {}
        span(1) { kind CLIENT }
      })
    }
  }
}
