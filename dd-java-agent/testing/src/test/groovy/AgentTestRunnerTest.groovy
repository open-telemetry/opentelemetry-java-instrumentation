import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.tooling.AgentInstaller

import java.lang.reflect.Field

import datadog.trace.agent.test.AgentTestRunner

class AgentTestRunnerTest extends AgentTestRunner {
  private static final ClassLoader BOOTSTRAP_CLASSLOADER = null

  static {
    // when test class initializes, opentracing should be set up, but not the agent.
    assert io.opentracing.Tracer.getClassLoader() == BOOTSTRAP_CLASSLOADER
    assert getAgentTransformer() == null
  }

  def "classpath setup"() {
    expect:
    io.opentracing.Tracer.getClassLoader() == BOOTSTRAP_CLASSLOADER
    TEST_TRACER == TestUtils.getUnderlyingGlobalTracer()
    getAgentTransformer() != null
    datadog.trace.api.Trace.getClassLoader() == BOOTSTRAP_CLASSLOADER
  }

  def "logging works"() {
    when:
    org.slf4j.LoggerFactory.getLogger(AgentTestRunnerTest).debug("hello")
    then:
    noExceptionThrown()
  }

  private static getAgentTransformer() {
    Field f
    try {
      f = AgentTestRunner.getDeclaredField("activeTransformer")
      f.setAccessible(true)
      return f.get(null)
    } finally {
      f.setAccessible(false)
    }
  }
}
