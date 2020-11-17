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
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.javaagent.tooling.muzzle.Reference
import io.opentelemetry.javaagent.tooling.muzzle.collector.ReferenceCollector

class ReferenceCollectorTest extends AgentTestRunner {
  def "method body creates references"() {
    setup:
    def references = ReferenceCollector.collectReferencesFrom(MethodBodyAdvice.name)

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
    assertMethod bRef, 'aMethod', '(Ljava/lang/String;)Ljava/lang/String;',
      MinimumVisibilityFlag.PROTECTED_OR_HIGHER,
      OwnershipFlag.NON_STATIC
    assertMethod bRef, 'aMethodWithPrimitives', '(Z)V',
      MinimumVisibilityFlag.PROTECTED_OR_HIGHER,
      OwnershipFlag.NON_STATIC
    assertMethod bRef, 'aStaticMethod', '()V',
      MinimumVisibilityFlag.PROTECTED_OR_HIGHER,
      OwnershipFlag.STATIC
    assertMethod bRef, 'aMethodWithArrays', '([Ljava/lang/String;)[Ljava/lang/Object;',
      MinimumVisibilityFlag.PROTECTED_OR_HIGHER,
      OwnershipFlag.NON_STATIC

    // field refs
    bRef.fields.isEmpty()
    aRef.fields.size() == 2
    assertField aRef, 'b', MinimumVisibilityFlag.PACKAGE_OR_HIGHER, OwnershipFlag.NON_STATIC
    assertField aRef, 'staticB', MinimumVisibilityFlag.PACKAGE_OR_HIGHER, OwnershipFlag.STATIC
  }

  def "protected ref test"() {
    setup:
    def references = ReferenceCollector.collectReferencesFrom(MethodBodyAdvice.B2.name)

    expect:
    assertMethod references[MethodBodyAdvice.B.name], 'protectedMethod', '()V',
      MinimumVisibilityFlag.PROTECTED_OR_HIGHER,
      OwnershipFlag.NON_STATIC
  }

  def "ldc creates references"() {
    setup:
    def references = ReferenceCollector.collectReferencesFrom(LdcAdvice.name)

    expect:
    references[MethodBodyAdvice.A.name] != null
  }

  def "instanceof creates references"() {
    setup:
    def references = ReferenceCollector.collectReferencesFrom(TestClasses.InstanceofAdvice.name)

    expect:
    references[MethodBodyAdvice.A.name] != null
  }

  def "invokedynamic creates references"() {
    setup:
    def references = ReferenceCollector.collectReferencesFrom(TestClasses.InvokeDynamicAdvice.name)

    expect:
    references['muzzle.TestClasses$MethodBodyAdvice$SomeImplementation'] != null
    references['muzzle.TestClasses$MethodBodyAdvice$B'] != null
  }

  def "should create references for helper classes"() {
    when:
    def references = ReferenceCollector.collectReferencesFrom(HelperAdvice.name)

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
}
