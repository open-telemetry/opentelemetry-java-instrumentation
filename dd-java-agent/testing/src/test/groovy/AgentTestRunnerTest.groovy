import com.google.common.reflect.ClassPath
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.SpockRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.tooling.Utils
import io.opentracing.Tracer

import java.lang.reflect.Field

class AgentTestRunnerTest extends AgentTestRunner {
  private static final ClassLoader BOOTSTRAP_CLASSLOADER = null
  private static final ClassLoader OT_LOADER
  private static final boolean AGENT_INSTALLED_IN_CLINIT
  // having opentracing class in test field should not cause problems
  private static final Tracer A_TRACER = null
  // having dd tracer api class in test field should not cause problems
  private static final datadog.trace.api.Tracer DD_API_TRACER = null

  static {
    // when test class initializes, opentracing should be set up, but not the agent.
    OT_LOADER = io.opentracing.Tracer.getClassLoader()
    AGENT_INSTALLED_IN_CLINIT = getAgentTransformer() != null
  }

  def "spock runner bootstrap prefixes correct for test setup"() {
    expect:
    SpockRunner.BOOTSTRAP_PACKAGE_PREFIXES_COPY == Utils.BOOTSTRAP_PACKAGE_PREFIXES
  }

  def "classpath setup"() {
    setup:
    final List<String> bootstrapClassesIncorrectlyLoaded = []
    for (ClassPath.ClassInfo info : TestUtils.getTestClasspath().getAllClasses()) {
      for (int i = 0; i < Utils.BOOTSTRAP_PACKAGE_PREFIXES.length; ++i) {
        if (info.getName().startsWith(Utils.BOOTSTRAP_PACKAGE_PREFIXES[i])) {
          Class<?> bootstrapClass = Class.forName(info.getName())
          if (bootstrapClass.getClassLoader() != BOOTSTRAP_CLASSLOADER) {
            bootstrapClassesIncorrectlyLoaded.add(bootstrapClass)
          }
          break
        }
      }
    }

    expect:
    A_TRACER == null
    DD_API_TRACER  == null
    !AGENT_INSTALLED_IN_CLINIT
    getTestTracer() == TestUtils.getUnderlyingGlobalTracer()
    getAgentTransformer() != null
    TestUtils.getUnderlyingGlobalTracer() == datadog.trace.api.GlobalTracer.get()
    bootstrapClassesIncorrectlyLoaded == []
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
