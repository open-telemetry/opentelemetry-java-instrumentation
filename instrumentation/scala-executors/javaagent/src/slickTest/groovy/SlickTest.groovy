/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

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
          errored false
          attributes {
          }
        }
        span(1) {
          name "SELECT ${SlickUtils.Db()}"
          kind CLIENT
          childOf span(0)
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "h2"
            "$SemanticAttributes.DB_NAME.key" SlickUtils.Db()
            "$SemanticAttributes.DB_USER.key" SlickUtils.Username()
            "$SemanticAttributes.DB_CONNECTION_STRING.key" "h2:mem:"
            "$SemanticAttributes.DB_STATEMENT.key" "SELECT ?"
            "$SemanticAttributes.DB_OPERATION.key" "SELECT"
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
