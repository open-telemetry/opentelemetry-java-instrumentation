import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import io.opentracing.Tracer

import java.lang.reflect.Field

class AgentTestRunnerTest extends AgentTestRunner {
  private static final ClassLoader BOOTSTRAP_CLASSLOADER = null
  private static final ClassLoader OT_LOADER
  private static final boolean AGENT_INSTALLED_IN_CLINIT
  // having opentracing class in test field should not cause problems
  private static final Tracer A_TRACER = null

  static {
    // when test class initializes, opentracing should be set up, but not the agent.
    OT_LOADER = io.opentracing.Tracer.getClassLoader()
    AGENT_INSTALLED_IN_CLINIT = getAgentTransformer() != null
  }

  def "classpath setup"() {
    expect:
    A_TRACER == null
    OT_LOADER == BOOTSTRAP_CLASSLOADER
    !AGENT_INSTALLED_IN_CLINIT
    getTestTracer() == TestUtils.getUnderlyingGlobalTracer()
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
