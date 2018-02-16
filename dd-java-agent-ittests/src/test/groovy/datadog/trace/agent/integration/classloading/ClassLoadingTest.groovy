package datadog.trace.agent.integration.classloading

import datadog.trace.agent.test.IntegrationTestUtils

import static datadog.trace.agent.test.IntegrationTestUtils.createJarWithClasses

import datadog.trace.api.Trace
import spock.lang.Specification


class ClassLoadingTest extends Specification {

  /** Assert that we can instrument classloaders which cannot resolve agent advice classes. */
  def "instrument classloader without agent classes" () {
    setup:
    final URL[] classpath = [createJarWithClasses(ClassToInstrument, Trace)]
    final URLClassLoader loader = new URLClassLoader(classpath, (ClassLoader)null)

    when:
    loader.loadClass("datadog.agent.TracingAgent")
    then:
    thrown ClassNotFoundException

    when:
    final Class<?> instrumentedClass = loader.loadClass(ClassToInstrument.getName())
    then:
    instrumentedClass.getClassLoader() == loader
  }

  def "can find bootstrap resources"() {
    expect:
    IntegrationTestUtils.getAgentClassLoader().getResources('datadog/trace/api/Trace.class') != null
  }
}
