package muzzle

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.tooling.muzzle.Reference
import datadog.trace.agent.tooling.muzzle.ReferenceCreator
import datadog.trace.agent.tooling.muzzle.ReferenceMatcher
import spock.lang.Shared

import static muzzle.TestClasses.*

class ReferenceMatcherTest extends AgentTestRunner {

  @Shared
  ClassLoader safeClasspath = new URLClassLoader([TestUtils.createJarWithClasses(MethodBodyAdvice.A,
    MethodBodyAdvice.B,
    MethodBodyAdvice.SomeInterface,
    MethodBodyAdvice.SomeImplementation)] as URL[],
    (ClassLoader) null)

  @Shared
  ClassLoader unsafeClasspath = new URLClassLoader([TestUtils.createJarWithClasses(MethodBodyAdvice.A,
    MethodBodyAdvice.SomeInterface,
    MethodBodyAdvice.SomeImplementation)] as URL[],
    (ClassLoader) null)

  def "match safe classpaths"() {
    setup:
    Reference[] refs = ReferenceCreator.createReferencesFrom(MethodBodyAdvice.getName(), this.getClass().getClassLoader()).values().toArray(new Reference[0])
    ReferenceMatcher refMatcher = new ReferenceMatcher(refs)

    expect:
    refMatcher.getMismatchedReferenceSources(safeClasspath).size() == 0
    refMatcher.getMismatchedReferenceSources(unsafeClasspath).size() == 1
  }
}
