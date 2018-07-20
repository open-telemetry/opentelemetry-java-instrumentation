package muzzle

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.tooling.muzzle.Reference
import datadog.trace.agent.tooling.muzzle.Reference.Method
import datadog.trace.agent.tooling.muzzle.Reference.Source
import datadog.trace.agent.tooling.muzzle.Reference.Flag
import datadog.trace.agent.tooling.muzzle.ReferenceCreator
import datadog.trace.agent.tooling.muzzle.ReferenceMatcher
import datadog.trace.agent.tooling.muzzle.UnloadedType
import net.bytebuddy.jar.asm.Type
import spock.lang.Shared

import java.lang.ref.WeakReference

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

  def "matching does not hold a strong reference to classloaders"() {
    expect:
    MuzzleWeakReferenceTest.classLoaderRefIsGarbageCollected()
  }

  def "match methods"() {
    setup:
    UnloadedType unloadedB = UnloadedType.of(MethodBodyAdvice.B.getName(), MethodBodyAdvice.B.getClassLoader())
    UnloadedType unloadedInterface = UnloadedType.of(MethodBodyAdvice.AnotherInterface.getName(), MethodBodyAdvice.AnotherInterface.getClassLoader())
    Method methodRef

    // match method declared in the class
    when:
    methodRef = new Method("aMethod", "(Ljava/lang/String;)Ljava/lang/String;")
    then:
    unloadedB.checkMatch(methodRef).size() == 0

    // match method declared in the supertype
    when:
    methodRef = new Method("hashCode", "()I")
    then:
    unloadedB.checkMatch(methodRef).size() == 0

    // match method declared in interface
    when:
    methodRef = new Method("someMethod", "()V")
    then:
    unloadedInterface.checkMatch(methodRef).size() == 0

    // missing method mismatch
    when:
    methodRef = new Method(new Source[0], new Flag[0], "missingTestMethod", Type.VOID_TYPE, new Type[0])
    then:
    unloadedB.checkMatch(methodRef).size() == 1
  }
}
