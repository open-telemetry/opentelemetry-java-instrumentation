import datadog.trace.agent.test.AgentTestRunner
import spock.lang.Shared

class SlickTest extends AgentTestRunner {

  @Shared
  def database = new SlickUtils()

  def "Basic statement generates spans"() {
    setup:
    def future = database.startQuery(SlickUtils.TestQuery())
    def result = database.getResults(future)
    TEST_WRITER.waitForTraces(1)

    expect:
    result == SlickUtils.TestValue()

    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 2

    def rootSpan = trace[0]
    rootSpan.serviceName == "unnamed-java-app"
    rootSpan.resourceName == "SlickUtils.startQuery"

    def dbSpan = trace[1]
    dbSpan.context().operationName == "${SlickUtils.Driver()}.query"
    dbSpan.serviceName == SlickUtils.Driver()
    dbSpan.resourceName == SlickUtils.TestQuery()
    dbSpan.type == "sql"
    !dbSpan.context().getErrorFlag()
    dbSpan.context().parentId == rootSpan.spanId

    def tags = dbSpan.context().tags
    tags["db.type"] == SlickUtils.Driver()
    tags["db.user"] == null
    tags["span.kind"] == "client"
    tags["span.type"] == "sql"
    tags["component"] == "java-jdbc-prepared_statement"

    tags["db.jdbc.url"].contains(SlickUtils.Driver())
    tags["span.origin.type"] != null

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == 8
  }

  def "Concurrent requests do not throw exception"() {
    setup:
    def sleepFuture = database.startQuery(SlickUtils.SleepQuery())

    def future = database.startQuery(SlickUtils.TestQuery())
    def result = database.getResults(future)

    database.getResults(sleepFuture)
    TEST_WRITER.waitForTraces(2)

    expect:
    result == SlickUtils.TestValue()
    TEST_WRITER.size() == 2 // Since we have two DB queries

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 2
  }
}
