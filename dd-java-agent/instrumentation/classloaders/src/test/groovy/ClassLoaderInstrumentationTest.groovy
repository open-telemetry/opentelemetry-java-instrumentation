import datadog.opentracing.DDTracer
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils

import java.security.SecureClassLoader

class ClassLoaderInstrumentationTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.classloader.enabled", "true")
  }

  DDTracer tracer = Mock()

  def setup() {
    TestUtils.registerOrReplaceGlobalTracer(tracer);
  }

  def "creating classloader calls register on tracer"() {
    when:
    new EmptyNonDelegatingLoader()

    then:
    1 * tracer.registerClassLoader(_ as EmptyNonDelegatingLoader)
    0 * _
  }

  def "creating anonymous classloader calls register on tracer"() {
    when:
    new EmptyNonDelegatingLoader() {}

    then:
    1 * tracer.registerClassLoader(_ as EmptyNonDelegatingLoader)
    0 * _
  }

  def "bootstrap classloaders aren't instrumented"() {
    // (Because they don't have access to GlobalTracer)
    when:
    new SecureClassLoader()
    new SecureClassLoader(null) {}
    new URLClassLoader(new URL[0], ClassLoader.systemClassLoader)

    then:
    0 * tracer.registerClassLoader(_)
    0 * _
  }

  class EmptyNonDelegatingLoader extends SecureClassLoader {
    EmptyNonDelegatingLoader() {
      super(null)
    }
  }
}
