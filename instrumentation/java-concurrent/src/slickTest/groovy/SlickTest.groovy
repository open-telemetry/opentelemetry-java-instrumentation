/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner

import static io.opentelemetry.trace.Span.Kind.CLIENT

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
          tags {
          }
        }
        span(1) {
          operationName SlickUtils.TestQuery()
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" SlickUtils.Driver()
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" SlickUtils.Driver()
            "$Tags.DB_INSTANCE" SlickUtils.Db()
            "$Tags.DB_USER" SlickUtils.Username()
            "$Tags.DB_STATEMENT" SlickUtils.TestQuery()
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
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
