package datadog.trace.agent.test

import datadog.trace.agent.tooling.DDLocationStrategy
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.ClassFileLocator
import spock.lang.Specification

class ResourceLocatingTest extends Specification {
  def "finds resources from parent classloader"() {
    setup:
    final String[] lastLookup = new String[1]
    ClassLoader childLoader = new ClassLoader(this.getClass().getClassLoader()) {
      @Override
      URL getResource(String name) {
        lastLookup[0] = name
        // do not delegate resource lookup
        return findResource(name)
      }
    }
    ClassFileLocator locator = new DDLocationStrategy().classFileLocator(childLoader, null)
    ClassFileLocator defaultLocator = AgentBuilder.LocationStrategy.ForClassLoader.STRONG.classFileLocator(childLoader, null)

    expect:
    locator.locate("java/lang/Object").isResolved()
    // lastLookup ensures childLoader was checked before parent for the resource
    lastLookup[0] == "java/lang/Object.class"
    (lastLookup[0] = null) == null

    !defaultLocator.locate("java/lang/Object").isResolved()
    lastLookup[0] == "java/lang/Object.class"
  }
}
