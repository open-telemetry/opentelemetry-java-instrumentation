import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.instrumentation.api.Tags

class SlickTest extends AgentTestRunner {

  // Can't be @Shared, otherwise the work queue is initialized before the instrumentation is applied
  def database = new SlickUtils()

  def "Basic statement generates spans"() {
    setup:
    def result = database.runQuery(SlickUtils.TestQuery())

    expect:
    result == SlickUtils.TestValue()

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "trace.annotation"
          parent()
          errored false
          tags {
            "$DDTags.RESOURCE_NAME" "SlickUtils.runQuery"
            "$Tags.COMPONENT" "trace"
          }
        }
        span(1) {
          operationName "database.query"
          childOf span(0)
          errored false
          tags {
            "$DDTags.SERVICE_NAME" SlickUtils.Driver()
            "$DDTags.RESOURCE_NAME" SlickUtils.TestQuery()
            "$DDTags.SPAN_TYPE" DDSpanTypes.SQL
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
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
}
