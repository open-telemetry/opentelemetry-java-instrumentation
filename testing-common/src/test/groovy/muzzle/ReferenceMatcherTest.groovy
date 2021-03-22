/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package muzzle

import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.ManifestationFlag.ABSTRACT
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.ManifestationFlag.INTERFACE
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.ManifestationFlag.NON_INTERFACE
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.MinimumVisibilityFlag.PRIVATE_OR_HIGHER
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.MinimumVisibilityFlag.PROTECTED_OR_HIGHER
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.OwnershipFlag.NON_STATIC
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.OwnershipFlag.STATIC
import static io.opentelemetry.javaagent.tooling.muzzle.matcher.Mismatch.MissingClass
import static io.opentelemetry.javaagent.tooling.muzzle.matcher.Mismatch.MissingField
import static io.opentelemetry.javaagent.tooling.muzzle.matcher.Mismatch.MissingFlag
import static io.opentelemetry.javaagent.tooling.muzzle.matcher.Mismatch.MissingMethod
import static muzzle.TestClasses.MethodBodyAdvice

import io.opentelemetry.instrumentation.TestHelperClasses
import io.opentelemetry.instrumentation.test.utils.ClasspathUtils
import io.opentelemetry.javaagent.tooling.muzzle.Reference
import io.opentelemetry.javaagent.tooling.muzzle.Reference.Source
import io.opentelemetry.javaagent.tooling.muzzle.collector.ReferenceCollector
import io.opentelemetry.javaagent.tooling.muzzle.matcher.Mismatch
import io.opentelemetry.javaagent.tooling.muzzle.matcher.ReferenceMatcher
import net.bytebuddy.jar.asm.Type
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class ReferenceMatcherTest extends Specification {
  static final TEST_EXTERNAL_INSTRUMENTATION_PACKAGE = "com.external.otel.instrumentation"

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
    def collector = new ReferenceCollector({ false })
    collector.collectReferencesFromAdvice(MethodBodyAdvice.name)
    def refMatcher = createMatcher(collector.getReferences().values())

    expect:
    getMismatchClassSet(refMatcher.getMismatchedReferenceSources(safeClasspath)).empty
    getMismatchClassSet(refMatcher.getMismatchedReferenceSources(unsafeClasspath)) == [MissingClass] as Set
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
    def cl = new CountingClassLoader(
      [ClasspathUtils.createJarWithClasses(MethodBodyAdvice.A,
        MethodBodyAdvice.B,
        MethodBodyAdvice.SomeInterface,
        MethodBodyAdvice.SomeImplementation)] as URL[],
      (ClassLoader) null)

    def collector = new ReferenceCollector({ false })
    collector.collectReferencesFromAdvice(MethodBodyAdvice.name)

    def refMatcher1 = createMatcher(collector.getReferences().values())
    def refMatcher2 = createMatcher(collector.getReferences().values())
    assert getMismatchClassSet(refMatcher1.getMismatchedReferenceSources(cl)).empty
    int countAfterFirstMatch = cl.count
    // the second matcher should be able to used cached type descriptions from the first
    assert getMismatchClassSet(refMatcher2.getMismatchedReferenceSources(cl)).empty

    expect:
    cl.count == countAfterFirstMatch
  }

  def "matching ref #referenceName #referenceFlags against #classToCheck produces #expectedMismatches"() {
    setup:
    def ref = new Reference.Builder(referenceName)
      .withFlag(referenceFlag)
      .build()

    when:
    def mismatches = createMatcher([ref]).getMismatchedReferenceSources(this.class.classLoader)

    then:
    getMismatchClassSet(mismatches) == expectedMismatches as Set

    where:
    referenceName           | referenceFlag | classToCheck       | expectedMismatches
    MethodBodyAdvice.B.name | NON_INTERFACE | MethodBodyAdvice.B | []
    MethodBodyAdvice.B.name | INTERFACE     | MethodBodyAdvice.B | [MissingFlag]
  }

  def "method match #methodTestDesc"() {
    setup:
    def methodType = Type.getMethodType(methodDesc)
    def reference = new Reference.Builder(classToCheck.name)
      .withMethod(new Source[0], methodFlags as Reference.Flag[], methodName, methodType.returnType, methodType.argumentTypes)
      .build()

    when:
    def mismatches = createMatcher([reference])
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    getMismatchClassSet(mismatches) == expectedMismatches as Set

    where:
    methodName      | methodDesc                               | methodFlags           | classToCheck                   | expectedMismatches | methodTestDesc
    "method"        | "(Ljava/lang/String;)Ljava/lang/String;" | []                    | MethodBodyAdvice.B             | []                 | "match method declared in class"
    "hashCode"      | "()I"                                    | []                    | MethodBodyAdvice.B             | []                 | "match method declared in superclass"
    "someMethod"    | "()V"                                    | []                    | MethodBodyAdvice.SomeInterface | []                 | "match method declared in interface"
    "privateStuff"  | "()V"                                    | [PRIVATE_OR_HIGHER]   | MethodBodyAdvice.B             | []                 | "match private method"
    "privateStuff"  | "()V"                                    | [PROTECTED_OR_HIGHER] | MethodBodyAdvice.B2            | [MissingFlag]      | "fail match private in supertype"
    "staticMethod"  | "()V"                                    | [NON_STATIC]          | MethodBodyAdvice.B             | [MissingFlag]      | "static method mismatch"
    "missingMethod" | "()V"                                    | []                    | MethodBodyAdvice.B             | [MissingMethod]    | "missing method mismatch"
  }

  def "field match #fieldTestDesc"() {
    setup:
    def reference = new Reference.Builder(classToCheck.name)
      .withField(new Source[0], fieldFlags as Reference.Flag[], fieldName, Type.getType(fieldType))
      .build()

    when:
    def mismatches = createMatcher([reference])
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    getMismatchClassSet(mismatches) == expectedMismatches as Set

    where:
    fieldName        | fieldType                                        | fieldFlags                    | classToCheck        | expectedMismatches | fieldTestDesc
    "missingField"   | "Ljava/lang/String;"                             | []                            | MethodBodyAdvice.A  | [MissingField]     | "mismatch missing field"
    "privateField"   | "Ljava/lang/String;"                             | []                            | MethodBodyAdvice.A  | [MissingField]     | "mismatch field type signature"
    "privateField"   | "Ljava/lang/Object;"                             | [PRIVATE_OR_HIGHER]           | MethodBodyAdvice.A  | []                 | "match private field"
    "privateField"   | "Ljava/lang/Object;"                             | [PROTECTED_OR_HIGHER]         | MethodBodyAdvice.A2 | [MissingFlag]      | "mismatch private field in supertype"
    "protectedField" | "Ljava/lang/Object;"                             | [STATIC]                      | MethodBodyAdvice.A  | [MissingFlag]      | "mismatch static field"
    "staticB"        | Type.getType(MethodBodyAdvice.B).getDescriptor() | [STATIC, PROTECTED_OR_HIGHER] | MethodBodyAdvice.A  | []                 | "match static field"
  }

  def "should ignore helper classes from third-party packages"() {
    given:
    def emptyClassLoader = new URLClassLoader(new URL[0], (ClassLoader) null)
    def reference = new Reference.Builder("com.google.common.base.Strings")
      .build()

    when:
    def mismatches = createMatcher([reference], [reference.className])
      .getMismatchedReferenceSources(emptyClassLoader)

    then:
    mismatches.empty
  }

  def "should not check abstract #desc helper classes"() {
    given:
    def reference = new Reference.Builder("io.opentelemetry.instrumentation.Helper")
      .withSuperName(TestHelperClasses.HelperSuperClass.name)
      .withFlag(ABSTRACT)
      .withMethod(new Source[0], [ABSTRACT] as Reference.Flag[], "unimplemented", Type.VOID_TYPE)
      .build()

    when:
    def mismatches = createMatcher([reference], [reference.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    mismatches.empty

    where:
    desc       | className
    "internal" | "com.external.otel.instrumentation.Helper"
    "external" | "${TEST_EXTERNAL_INSTRUMENTATION_PACKAGE}.Helper"
  }

  def "should not check #desc helper classes with no supertypes"() {
    given:
    def reference = new Reference.Builder(className)
      .withSuperName(Object.name)
      .withMethod(new Source[0], [] as Reference.Flag[], "someMethod", Type.VOID_TYPE)
      .build()

    when:
    def mismatches = createMatcher([reference], [reference.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    mismatches.empty

    where:
    desc       | className
    "internal" | "com.external.otel.instrumentation.Helper"
    "external" | "${TEST_EXTERNAL_INSTRUMENTATION_PACKAGE}.Helper"
  }

  def "should fail #desc helper classes that does not implement all abstract methods"() {
    given:
    def reference = new Reference.Builder(className)
      .withSuperName(TestHelperClasses.HelperSuperClass.name)
      .withMethod(new Source[0], [] as Reference.Flag[], "someMethod", Type.VOID_TYPE)
      .build()

    when:
    def mismatches = createMatcher([reference], [reference.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    getMismatchClassSet(mismatches) == [MissingMethod] as Set

    where:
    desc       | className
    "internal" | "com.external.otel.instrumentation.Helper"
    "external" | "${TEST_EXTERNAL_INSTRUMENTATION_PACKAGE}.Helper"
  }

  def "should fail #desc helper classes that do not implement all abstract methods - even if empty abstract class reference exists"() {
    given:
    def emptySuperClassRef = new Reference.Builder(TestHelperClasses.HelperSuperClass.name)
      .build()
    def reference = new Reference.Builder(className)
      .withSuperName(TestHelperClasses.HelperSuperClass.name)
      .withMethod(new Source[0], [] as Reference.Flag[], "someMethod", Type.VOID_TYPE)
      .build()

    when:
    def mismatches = createMatcher([reference, emptySuperClassRef], [reference.className, emptySuperClassRef.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    getMismatchClassSet(mismatches) == [MissingMethod] as Set

    where:
    desc       | className
    "internal" | "com.external.otel.instrumentation.Helper"
    "external" | "${TEST_EXTERNAL_INSTRUMENTATION_PACKAGE}.Helper"
  }

  def "should check #desc helper class whether interface methods are implemented in the super class"() {
    given:
    def baseHelper = new Reference.Builder("io.opentelemetry.instrumentation.BaseHelper")
      .withSuperName(Object.name)
      .withInterface(TestHelperClasses.HelperInterface.name)
      .withMethod(new Source[0], [] as Reference.Flag[], "foo", Type.VOID_TYPE)
      .build()
    // abstract HelperInterface#foo() is implemented by BaseHelper
    def helper = new Reference.Builder(className)
      .withSuperName(baseHelper.className)
      .withInterface(TestHelperClasses.AnotherHelperInterface.name)
      .withMethod(new Source[0], [] as Reference.Flag[], "bar", Type.VOID_TYPE)
      .build()

    when:
    def mismatches = createMatcher([helper, baseHelper], [helper.className, baseHelper.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    mismatches.empty

    where:
    desc       | className
    "internal" | "com.external.otel.instrumentation.Helper"
    "external" | "${TEST_EXTERNAL_INSTRUMENTATION_PACKAGE}.Helper"
  }

  private static ReferenceMatcher createMatcher(Collection<Reference> references = [],
                                                List<String> helperClasses = []) {
    new ReferenceMatcher(helperClasses, references as Reference[], { it.startsWith(TEST_EXTERNAL_INSTRUMENTATION_PACKAGE) })
  }

  private static Set<Class> getMismatchClassSet(List<Mismatch> mismatches) {
    Set<Class> mismatchClasses = new HashSet<>(mismatches.size())
    for (Mismatch mismatch : mismatches) {
      mismatchClasses.add(mismatch.class)
    }
    return mismatchClasses
  }
}
