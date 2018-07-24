package muzzle

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.tooling.Utils
import datadog.trace.agent.tooling.muzzle.Reference
import datadog.trace.agent.tooling.muzzle.Reference.Method
import datadog.trace.agent.tooling.muzzle.Reference.Source
import datadog.trace.agent.tooling.muzzle.Reference.Flag
import datadog.trace.agent.tooling.muzzle.ReferenceCreator
import datadog.trace.agent.tooling.muzzle.ReferenceMatcher

import net.bytebuddy.jar.asm.Type
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

  def "matching does not hold a strong reference to classloaders"() {
    expect:
    MuzzleWeakReferenceTest.classLoaderRefIsGarbageCollected()
  }

  def "match classes"() {
    ReferenceMatcher.UnloadedType unloadedB = ReferenceMatcher.UnloadedType.of(MethodBodyAdvice.B.getName(), MethodBodyAdvice.B.getClassLoader())
    Reference ref

    when:
    ref = new Reference.Builder(Utils.getInternalName(MethodBodyAdvice.B.getName()))
      .withFlag(Flag.NON_INTERFACE)
      .build()
    then:
    unloadedB.checkMatch(ref).size() == 0

    when:
    ref = new Reference.Builder(Utils.getInternalName(MethodBodyAdvice.B.getName()))
      .withFlag(Flag.INTERFACE)
      .build()
    then:
    unloadedB.checkMatch(ref).size() == 1
  }

  def "match methods"() {
    setup:
    ReferenceMatcher.UnloadedType unloadedB = ReferenceMatcher.UnloadedType.of(MethodBodyAdvice.B.getName(), MethodBodyAdvice.B.getClassLoader())
    ReferenceMatcher.UnloadedType unloadedB2 = ReferenceMatcher.UnloadedType.of(MethodBodyAdvice.B2.getName(), MethodBodyAdvice.B2.getClassLoader())
    ReferenceMatcher.UnloadedType unloadedInterface = ReferenceMatcher.UnloadedType.of(MethodBodyAdvice.AnotherInterface.getName(), MethodBodyAdvice.AnotherInterface.getClassLoader())
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

    // match private method in the class
    when:
    methodRef = new Method("privateStuff", "()V")
    then:
    unloadedB.checkMatch(methodRef).size() == 0

    // fail to match private method in superclass
    when:
    methodRef = new Method("privateStuff", "()V")
    then:
    unloadedB2.checkMatch(methodRef).size() == 1

    // static method flag mismatch
    when:
    methodRef = new Method(new Source[0], [Flag.NON_STATIC] as Flag[], "aStaticMethod", Type.getType("V"))
    then:
    unloadedB2.checkMatch(methodRef).size() == 1

    // missing method mismatch
    when:
    methodRef = new Method(new Source[0], new Flag[0], "missingTestMethod", Type.VOID_TYPE, new Type[0])
    then:
    unloadedB.checkMatch(methodRef).size() == 1
  }
}
