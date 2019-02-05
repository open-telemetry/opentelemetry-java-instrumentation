package muzzle

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.ClasspathUtils
import datadog.trace.agent.tooling.muzzle.Reference
import datadog.trace.agent.tooling.muzzle.Reference.Source
import datadog.trace.agent.tooling.muzzle.ReferenceCreator
import datadog.trace.agent.tooling.muzzle.ReferenceMatcher
import net.bytebuddy.jar.asm.Type
import spock.lang.Shared

import static datadog.trace.agent.tooling.muzzle.Reference.Flag.INTERFACE
import static datadog.trace.agent.tooling.muzzle.Reference.Flag.NON_INTERFACE
import static datadog.trace.agent.tooling.muzzle.Reference.Flag.NON_STATIC
import static datadog.trace.agent.tooling.muzzle.Reference.Flag.PRIVATE_OR_HIGHER
import static datadog.trace.agent.tooling.muzzle.Reference.Flag.PROTECTED_OR_HIGHER
import static datadog.trace.agent.tooling.muzzle.Reference.Flag.STATIC
import static datadog.trace.agent.tooling.muzzle.Reference.Mismatch.MissingClass
import static datadog.trace.agent.tooling.muzzle.Reference.Mismatch.MissingField
import static datadog.trace.agent.tooling.muzzle.Reference.Mismatch.MissingFlag
import static datadog.trace.agent.tooling.muzzle.Reference.Mismatch.MissingMethod
import static muzzle.TestClasses.MethodBodyAdvice

class ReferenceMatcherTest extends AgentTestRunner {

  @Shared
  ClassLoader safeClasspath = new URLClassLoader([ClasspathUtils.createJarWithClasses(MethodBodyAdvice.A,
    MethodBodyAdvice.B,
    MethodBodyAdvice.SomeInterface,
    MethodBodyAdvice.SomeImplementation)] as URL[],
    (ClassLoader) null)

  @Shared
  ClassLoader unsafeClasspath = new URLClassLoader([ClasspathUtils.createJarWithClasses(MethodBodyAdvice.A,
    MethodBodyAdvice.SomeInterface,
    MethodBodyAdvice.SomeImplementation)] as URL[],
    (ClassLoader) null)

  def "match safe classpaths"() {
    setup:
    Reference[] refs = ReferenceCreator.createReferencesFrom(MethodBodyAdvice.getName(), this.getClass().getClassLoader()).values().toArray(new Reference[0])
    ReferenceMatcher refMatcher = new ReferenceMatcher(refs)

    expect:
    getMismatchClassSet(refMatcher.getMismatchedReferenceSources(safeClasspath)) == new HashSet<>()
    getMismatchClassSet(refMatcher.getMismatchedReferenceSources(unsafeClasspath)) == new HashSet<>([MissingClass])
  }

  def "matching does not hold a strong reference to classloaders"() {
    expect:
    MuzzleWeakReferenceTest.classLoaderRefIsGarbageCollected()
  }

  private static class CountingClassLoader extends URLClassLoader {
    int count = 0

    CountingClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, (ClassLoader) parent)
    }

    @Override
    URL getResource(String name) {
      count++
      return super.getResource(name)
    }
  }

  def "muzzle type pool caches"() {
    setup:
    ClassLoader cl = new CountingClassLoader(
      [ClasspathUtils.createJarWithClasses(MethodBodyAdvice.A,
        MethodBodyAdvice.B,
        MethodBodyAdvice.SomeInterface,
        MethodBodyAdvice.SomeImplementation)] as URL[],
      (ClassLoader) null)
    Reference[] refs = ReferenceCreator.createReferencesFrom(MethodBodyAdvice.getName(), this.getClass().getClassLoader()).values().toArray(new Reference[0])
    ReferenceMatcher refMatcher1 = new ReferenceMatcher(refs)
    ReferenceMatcher refMatcher2 = new ReferenceMatcher(refs)
    assert getMismatchClassSet(refMatcher1.getMismatchedReferenceSources(cl)) == new HashSet<>()
    int countAfterFirstMatch = cl.count
    // the second matcher should be able to used cached type descriptions from the first
    assert getMismatchClassSet(refMatcher2.getMismatchedReferenceSources(cl)) == new HashSet<>()

    expect:
    cl.count == countAfterFirstMatch
  }

  def "matching ref #referenceName #referenceFlags against #classToCheck produces #expectedMismatches"() {
    setup:
    Reference.Builder builder = new Reference.Builder(referenceName)
    for (Reference.Flag refFlag : referenceFlags) {
      builder = builder.withFlag(refFlag)
    }
    Reference ref = builder.build()

    expect:
    getMismatchClassSet(ReferenceMatcher.checkMatch(ref, this.getClass().getClassLoader())) == new HashSet<Object>(expectedMismatches)

    where:
    referenceName                | referenceFlags  | classToCheck       | expectedMismatches
    MethodBodyAdvice.B.getName() | [NON_INTERFACE] | MethodBodyAdvice.B | []
    MethodBodyAdvice.B.getName() | [INTERFACE]     | MethodBodyAdvice.B | [MissingFlag]
  }

  def "method match #methodTestDesc"() {
    setup:
    Type methodType = Type.getMethodType(methodDesc)
    Reference reference = new Reference.Builder(classToCheck.getName())
      .withMethod(new Source[0], methodFlags as Reference.Flag[], methodName, methodType.getReturnType(), methodType.getArgumentTypes())
      .build()

    expect:
    getMismatchClassSet(ReferenceMatcher.checkMatch(reference, this.getClass().getClassLoader())) == new HashSet<Object>(expectedMismatches)

    where:
    methodName      | methodDesc                               | methodFlags           | classToCheck                   | expectedMismatches | methodTestDesc
    "aMethod"       | "(Ljava/lang/String;)Ljava/lang/String;" | []                    | MethodBodyAdvice.B             | []                 | "match method declared in class"
    "hashCode"      | "()I"                                    | []                    | MethodBodyAdvice.B             | []                 | "match method declared in superclass"
    "someMethod"    | "()V"                                    | []                    | MethodBodyAdvice.SomeInterface | []                 | "match method declared in interface"
    "privateStuff"  | "()V"                                    | [PRIVATE_OR_HIGHER]   | MethodBodyAdvice.B             | []                 | "match private method"
    "privateStuff"  | "()V"                                    | [PROTECTED_OR_HIGHER] | MethodBodyAdvice.B2            | [MissingFlag]      | "fail match private in supertype"
    "aStaticMethod" | "()V"                                    | [NON_STATIC]          | MethodBodyAdvice.B             | [MissingFlag]      | "static method mismatch"
    "missingMethod" | "()V"                                    | []                    | MethodBodyAdvice.B             | [MissingMethod]    | "missing method mismatch"
  }

  def "field match #fieldTestDesc"() {
    setup:
    Reference reference = new Reference.Builder(classToCheck.getName())
      .withField(new Source[0], fieldFlags as Reference.Flag[], fieldName, Type.getType(fieldType))
      .build()

    expect:
    getMismatchClassSet(ReferenceMatcher.checkMatch(reference, this.getClass().getClassLoader())) == new HashSet<Object>(expectedMismatches)

    where:
    fieldName        | fieldType                                        | fieldFlags                    | classToCheck        | expectedMismatches | fieldTestDesc
    "missingField"   | "Ljava/lang/String;"                             | []                            | MethodBodyAdvice.A  | [MissingField]     | "mismatch missing field"
    "privateField"   | "Ljava/lang/String;"                             | []                            | MethodBodyAdvice.A  | [MissingField]     | "mismatch field type signature"
    "privateField"   | "Ljava/lang/Object;"                             | [PRIVATE_OR_HIGHER]           | MethodBodyAdvice.A  | []                 | "match private field"
    "privateField"   | "Ljava/lang/Object;"                             | [PROTECTED_OR_HIGHER]         | MethodBodyAdvice.A2 | [MissingFlag]      | "mismatch private field in supertype"
    "protectedField" | "Ljava/lang/Object;"                             | [STATIC]                      | MethodBodyAdvice.A  | [MissingFlag]      | "mismatch static field"
    "staticB"        | Type.getType(MethodBodyAdvice.B).getDescriptor() | [STATIC, PROTECTED_OR_HIGHER] | MethodBodyAdvice.A  | []                 | "match static field"
  }

  private static Set<Class> getMismatchClassSet(List<Reference.Mismatch> mismatches) {
    final Set<Class> mismatchClasses = new HashSet<>(mismatches.size())
    for (Reference.Mismatch mismatch : mismatches) {
      mismatchClasses.add(mismatch.getClass())
    }
    return mismatchClasses
  }
}
