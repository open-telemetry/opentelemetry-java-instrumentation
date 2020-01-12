import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner

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
            "$MoreTags.RESOURCE_NAME" "SlickUtils.runQuery"
            "$Tags.COMPONENT" "trace"
          }
        }
        span(1) {
          operationName "database.query"
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" SlickUtils.Driver()
            "$MoreTags.RESOURCE_NAME" SlickUtils.TestQuery()
            "$MoreTags.SPAN_TYPE" SpanTypes.SQL
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
