import datadog.trace.agent.test.TestUtils

import java.lang.reflect.Field

import static datadog.trace.agent.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER

import datadog.trace.agent.test.AgentTestRunner

class AgentTestRunnerTest extends AgentTestRunner {
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
  }

  def "logging works"() {
    when:
    org.slf4j.LoggerFactory.getLogger(AgentTestRunnerTest).debug("hello")
    then:
    noExceptionThrown()
  }

  def "can't see agent classes"() {
    // TODO
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
