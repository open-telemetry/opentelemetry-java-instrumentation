package datadog.trace.agent.test

import datadog.trace.agent.tooling.DDLocationStrategy
import java.util.concurrent.atomic.AtomicReference
import net.bytebuddy.agent.builder.AgentBuilder
import spock.lang.Shared
import spock.lang.Specification

class ResourceLocatingTest extends Specification {
  @Shared
  def lastLookup = new AtomicReference<String>()
  @Shared
  def childLoader = new ClassLoader(this.getClass().getClassLoader()) {
    @Override
    URL getResource(String name) {
      lastLookup.set(name)
      // do not delegate resource lookup
      return findResource(name)
    }
  }

  def cleanup() {
    lastLookup.set(null)
  }

  def "finds resources from parent classloader"() {
    expect:
    locator.locate("java/lang/Object").isResolved() == usesProvidedClassloader
    // lastLookup verifies that the given classloader is only used when expected
    lastLookup.get() == usesProvidedClassloader ? null : "java/lang/Object.class"

    and:
    !locator.locate("java/lang/InvalidClass").isResolved()
    lastLookup.get() == "java/lang/InvalidClass.class"

    where:
    locator                                                                                 | usesProvidedClassloader
    new DDLocationStrategy().classFileLocator(childLoader, null)                            | true
    AgentBuilder.LocationStrategy.ForClassLoader.STRONG.classFileLocator(childLoader, null) | false
  }
}
