import datadog.trace.agent.test.AgentTestRunner
import io.dropwizard.testing.junit.ResourceTestRule
import org.junit.ClassRule
import spock.lang.Shared

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class JerseyTest extends AgentTestRunner {

  @Shared
  @ClassRule
  ResourceTestRule resources = ResourceTestRule.builder().addResource(new TestResource()).build()

  def "test resource"() {
    setup:
    // start a trace because the test doesn't go through any servlet or other instrumentation.
    def response = runUnderTrace("test.span") {
      resources.client().resource("/test/hello/bob").post(String)
    }

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
