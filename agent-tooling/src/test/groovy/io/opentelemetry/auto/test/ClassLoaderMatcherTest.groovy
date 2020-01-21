package io.opentelemetry.auto.test

import io.opentelemetry.auto.bootstrap.AgentClassLoader
import io.opentelemetry.auto.tooling.ClassLoaderMatcher
import io.opentelemetry.auto.util.test.AgentSpecification

class ClassLoaderMatcherTest extends AgentSpecification {

  def "skip non-delegating classloader"() {
    setup:
    final URLClassLoader badLoader = new NonDelegatingClassLoader()
    expect:
    ClassLoaderMatcher.skipClassLoader().matches(badLoader)
  }

  def "skips agent classloader"() {
    setup:
    URL root = new URL("file://")
    final URLClassLoader agentLoader = new AgentClassLoader(root, null, null)
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

  /*
   * A URLClassloader which only delegates java.* classes
   */

  private static class NonDelegatingClassLoader extends URLClassLoader {
    NonDelegatingClassLoader() {
      super(new URL[0], (ClassLoader) null)
    }

    @Override
    Class<?> loadClass(String className) {
      if (className.startsWith("java.")) {
        return super.loadClass(className)
      }
      throw new ClassNotFoundException(className)
    }
  }

}
