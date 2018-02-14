import datadog.trace.agent.test.AgentTestRunner
import io.dropwizard.testing.junit.ResourceTestRule
import org.junit.ClassRule
import spock.lang.Shared

class JerseyTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.jax-rs.enabled", "true")
  }

  @Shared
  @ClassRule
  ResourceTestRule resources = ResourceTestRule.builder().addResource(new TestResource()).build()

  def "test resource"() {
    setup:
    // start a trace because the test doesn't go through any servlet or other instrumentation.
    def scope = TEST_TRACER.buildSpan("test.span").startActive(true)
    def response = resources.client().resource("/test/hello/bob").post(String)
    scope.close()

    expect:
    response == "Hello bob!"
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    def span = trace[0]
    span.resourceName == "POST /test/hello/{name}"
    span.tags["component"] == "jax-rs"
  }
}
