/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.trace.Span.Kind.CLIENT

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.instrumentation.auto.jdbc.JDBCUtils
import io.opentelemetry.trace.attributes.SemanticAttributes

class SlickTest extends AgentTestRunner {

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
          operationName "run query"
          parent()
          errored false
          attributes {
          }
        }
        span(1) {
          operationName JDBCUtils.normalizeSql(SlickUtils.TestQuery())
          spanKind CLIENT
          childOf span(0)
          errored false
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "h2"
            "${SemanticAttributes.DB_NAME.key()}" SlickUtils.Db()
            "${SemanticAttributes.DB_USER.key()}" SlickUtils.Username()
            "${SemanticAttributes.DB_STATEMENT.key()}" JDBCUtils.normalizeSql(SlickUtils.TestQuery())
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "h2:mem:"
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
        span(1) { spanKind CLIENT }
      })
      trace(1, 2, {
        span(0) {}
        span(1) { spanKind CLIENT }
      })
    }
  }
}
