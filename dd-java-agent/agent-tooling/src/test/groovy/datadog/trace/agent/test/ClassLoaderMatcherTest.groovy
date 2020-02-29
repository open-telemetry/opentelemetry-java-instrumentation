package datadog.trace.agent.test

import datadog.trace.agent.tooling.ClassLoaderMatcher
import datadog.trace.bootstrap.DatadogClassLoader
import datadog.trace.util.test.DDSpecification

class ClassLoaderMatcherTest extends DDSpecification {

  def "skips agent classloader"() {
    setup:
    URL root = new URL("file://")
    final URLClassLoader agentLoader = new DatadogClassLoader(root, null, new DatadogClassLoader.BootstrapClassLoaderProxy(), null)
    expect:
    ClassLoaderMatcher.skipClassLoader().matches(agentLoader)
  }

  def "does not skip empty classloader"() {
    setup:
    final ClassLoader emptyLoader = new ClassLoader() {}
    expect:
    !ClassLoaderMatcher.skipClassLoader().matches(emptyLoader)
  }

  def "does not skip bootstrap classloader"() {
    expect:
    !ClassLoaderMatcher.skipClassLoader().matches(null)
  }

  def "DatadogClassLoader class name is hardcoded in ClassLoaderMatcher"() {
    expect:
    DatadogClassLoader.name == "datadog.trace.bootstrap.DatadogClassLoader"
  }
}
