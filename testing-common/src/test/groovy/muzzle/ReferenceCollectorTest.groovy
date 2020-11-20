/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package muzzle

import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.ManifestationFlag
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.MinimumVisibilityFlag
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.OwnershipFlag
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.VisibilityFlag
import static muzzle.TestClasses.HelperAdvice
import static muzzle.TestClasses.LdcAdvice
import static muzzle.TestClasses.MethodBodyAdvice

import io.opentelemetry.instrumentation.TestHelperClasses
import io.opentelemetry.instrumentation.TestHelperDepCycle
import io.opentelemetry.instrumentation.TestHelperDeps
import io.opentelemetry.instrumentation.TestHelperDepsEnum
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.javaagent.tooling.muzzle.Reference
import io.opentelemetry.javaagent.tooling.muzzle.collector.ReferenceCollector

class ReferenceCollectorTest extends AgentTestRunner {
  def "method body creates references"() {
    setup:
    def collector = new ReferenceCollector()
    collector.collectReferencesFrom(MethodBodyAdvice.name)
    def references = collector.getReferences()

    expect:
    references.keySet() == [
      MethodBodyAdvice.A.name,
      MethodBodyAdvice.B.name,
      MethodBodyAdvice.SomeInterface.name,
      MethodBodyAdvice.SomeImplementation.name
    ] as Set

    def bRef = references[MethodBodyAdvice.B.name]
    def aRef = references[MethodBodyAdvice.A.name]

    // interface flags
    bRef.flags.contains(ManifestationFlag.NON_INTERFACE)
    references[MethodBodyAdvice.SomeInterface.name].flags.contains(ManifestationFlag.INTERFACE)

    // class access flags
    aRef.flags.contains(MinimumVisibilityFlag.PACKAGE_OR_HIGHER)
    bRef.flags.contains(MinimumVisibilityFlag.PACKAGE_OR_HIGHER)

    // method refs
    assertMethod bRef, 'method', '(Ljava/lang/String;)Ljava/lang/String;',
      MinimumVisibilityFlag.PROTECTED_OR_HIGHER,
      OwnershipFlag.NON_STATIC
    assertMethod bRef, 'methodWithPrimitives', '(Z)V',
      MinimumVisibilityFlag.PROTECTED_OR_HIGHER,
      OwnershipFlag.NON_STATIC
    assertMethod bRef, 'staticMethod', '()V',
      MinimumVisibilityFlag.PROTECTED_OR_HIGHER,
      OwnershipFlag.STATIC
    assertMethod bRef, 'methodWithArrays', '([Ljava/lang/String;)[Ljava/lang/Object;',
      MinimumVisibilityFlag.PROTECTED_OR_HIGHER,
      OwnershipFlag.NON_STATIC

    // field refs
    bRef.fields.isEmpty()
    aRef.fields.size() == 2
    assertField aRef, 'publicB', MinimumVisibilityFlag.PACKAGE_OR_HIGHER, OwnershipFlag.NON_STATIC
    assertField aRef, 'staticB', MinimumVisibilityFlag.PACKAGE_OR_HIGHER, OwnershipFlag.STATIC
  }

  def "protected ref test"() {
    setup:
    def collector = new ReferenceCollector()
    collector.collectReferencesFrom(MethodBodyAdvice.B2.name)
    def references = collector.getReferences()

    expect:
    assertMethod references[MethodBodyAdvice.B.name], 'protectedMethod', '()V',
      MinimumVisibilityFlag.PROTECTED_OR_HIGHER,
      OwnershipFlag.NON_STATIC
  }

  def "ldc creates references"() {
    setup:
    def collector = new ReferenceCollector()
    collector.collectReferencesFrom(LdcAdvice.name)
    def references = collector.getReferences()

    expect:
    references[MethodBodyAdvice.A.name] != null
  }

  def "instanceof creates references"() {
    setup:
    def collector = new ReferenceCollector()
    collector.collectReferencesFrom(TestClasses.InstanceofAdvice.name)
    def references = collector.getReferences()

    expect:
    references[MethodBodyAdvice.A.name] != null
  }

  def "invokedynamic creates references"() {
    setup:
    def collector = new ReferenceCollector()
    collector.collectReferencesFrom(TestClasses.InvokeDynamicAdvice.name)
    def references = collector.getReferences()

    expect:
    references['muzzle.TestClasses$MethodBodyAdvice$SomeImplementation'] != null
    references['muzzle.TestClasses$MethodBodyAdvice$B'] != null
  }

  def "should create references for helper classes"() {
    when:
    def collector = new ReferenceCollector()
    collector.collectReferencesFrom(HelperAdvice.name)
    def references = collector.getReferences()

    then:
    references.keySet() == [
      TestHelperClasses.Helper.name,
      TestHelperClasses.HelperSuperClass.name,
      TestHelperClasses.HelperInterface.name
    ] as Set

    with(references[TestHelperClasses.HelperSuperClass.name]) { helperSuperClass ->
      helperSuperClass.flags.contains(ManifestationFlag.ABSTRACT)
      assertHelperSuperClassMethod(helperSuperClass, true)
      assertMethod helperSuperClass, 'finalMethod', '()Ljava/lang/String;',
        VisibilityFlag.PUBLIC,
        OwnershipFlag.NON_STATIC,
        ManifestationFlag.FINAL
    }

    with(references[TestHelperClasses.HelperInterface.name]) { helperInterface ->
      helperInterface.flags.contains(ManifestationFlag.ABSTRACT)
      assertHelperInterfaceMethod helperInterface, true
    }

    with(references[TestHelperClasses.Helper.name]) { helperClass ->
      helperClass.flags.contains(ManifestationFlag.NON_FINAL)
      assertHelperSuperClassMethod helperClass, false
      assertHelperInterfaceMethod helperClass, false
    }
  }

  def "should find all helper classes"() {
    when:
    def collector = new ReferenceCollector()
    collector.collectReferencesFrom(HelperAdvice.name)
    def helperClasses = collector.getSortedHelperClasses()

    then:
    helperClasses == [
      TestHelperClasses.HelperInterface.name,
      TestHelperClasses.HelperSuperClass.name,
      TestHelperClasses.Helper.name
    ]
  }

  def "should correctly sort helper classes topologically"() {
    when:
    def collector = new ReferenceCollector()
    collector.collectReferencesFrom(TestClasses.HelperDepsAdvice.name)
    def helperClasses = collector.getSortedHelperClasses()

    then:
    helperClasses == [
      TestHelperDeps.ThirdOne.name,
      TestHelperDeps.Bar.name,
      TestHelperDeps.SomeTestClass.name,
      TestHelperDeps.BarProvider.name,
      TestHelperDeps.Foo.name,
      TestHelperDeps.FooProvider.name,
      TestHelperDeps.name,
    ]
  }

  def "should deal with a dependency cycle"() {
    when:
    def collector = new ReferenceCollector()
    collector.collectReferencesFrom(TestClasses.HelperDepCycleAdvice.name)
    def helperClasses = collector.getSortedHelperClasses()

    then:
    helperClasses == [
      TestHelperDepCycle.ClassOne.name,
      TestHelperDepCycle.ClassTwo.name,
      TestHelperDepCycle.name
    ]
  }

  def "should deal with a dependency cycle with multiple advice classes"() {
    when:
    def collector = new ReferenceCollector()
    collector.collectReferencesFrom(TestClasses.HelperDepCycleAdvice.name)
    collector.collectReferencesFrom(TestClasses.HelperDepCycleSeparateAdvice.name)
    def helperClasses = collector.getSortedHelperClasses()

    then:
    helperClasses == [
      TestHelperDepCycle.ClassOne.name,
      TestHelperDepCycle.ClassTwo.name,
      TestHelperDepCycle.name
    ]
  }

  def "should deal with a dependency cycle between abstract & implementation classes"() {
    when:
    def collector = new ReferenceCollector()
    collector.collectReferencesFrom(TestClasses.HelperDepEnumCycle.name)
    def helperClasses = collector.getSortedHelperClasses()

    then:
    assertThatContainsInOrder helperClasses, [
      TestHelperDepsEnum.EnumWithOverridingClasses.getName(),
      TestHelperDepsEnum.name,
      TestHelperDepsEnum.EnumWithOverridingClasses.getName() + '$2',
      TestHelperDepsEnum.EnumWithOverridingClasses.getName() + '$1',
    ]
  }

  private static assertHelperSuperClassMethod(Reference reference, boolean isAbstract) {
    assertMethod reference, 'abstractMethod', '()I',
      VisibilityFlag.PROTECTED,
      OwnershipFlag.NON_STATIC,
      isAbstract ? ManifestationFlag.ABSTRACT : ManifestationFlag.NON_FINAL
  }

  private static assertHelperInterfaceMethod(Reference reference, boolean isAbstract) {
    assertMethod reference, 'foo', '()V',
      VisibilityFlag.PUBLIC,
      OwnershipFlag.NON_STATIC,
      isAbstract ? ManifestationFlag.ABSTRACT : ManifestationFlag.NON_FINAL
  }

  private static assertMethod(Reference reference, String methodName, String methodDesc, Reference.Flag... flags) {
    def method = findMethod reference, methodName, methodDesc
    method != null && (method.flags == flags as Set)
  }

  private static findMethod(Reference reference, String methodName, String methodDesc) {
    for (def method : reference.methods) {
      if (method == new Reference.Method(methodName, methodDesc)) {
        return method
      }
    }
    return null
  }

  private static assertField(Reference reference, String fieldName, Reference.Flag... flags) {
    def field = findField reference, fieldName
    field != null && (field.flags == flags as Set)
  }

  private static Reference.Field findField(Reference reference, String fieldName) {
    for (def field : reference.fields) {
      if (field.name == fieldName) {
        return field
      }
    }
    return null
  }

  private static assertThatContainsInOrder(List<String> list, List<String> sublist) {
    def listIt = list.iterator()
    def sublistIt = sublist.iterator()
    while (listIt.hasNext() && sublistIt.hasNext()) {
      def sublistElem = sublistIt.next()
      while (listIt.hasNext()) {
        def listElem = listIt.next()
        if (listElem == sublistElem) {
          break
        }
      }
    }
    return !sublistIt.hasNext()
  }
}
