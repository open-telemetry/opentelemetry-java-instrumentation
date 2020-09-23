/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package muzzle

import static muzzle.TestClasses.HelperAdvice
import static muzzle.TestClasses.LdcAdvice
import static muzzle.TestClasses.MethodBodyAdvice

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.instrumentation.TestHelperClasses
import io.opentelemetry.javaagent.tooling.muzzle.Reference
import io.opentelemetry.javaagent.tooling.muzzle.ReferenceCreator
import spock.lang.Ignore

class ReferenceCreatorTest extends AgentTestRunner {
  def "method body creates references"() {
    setup:
    def references = ReferenceCreator.createReferencesFrom(MethodBodyAdvice.name, this.class.classLoader)

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
    bRef.flags.contains(Reference.Flag.NON_INTERFACE)
    references[MethodBodyAdvice.SomeInterface.name].flags.contains(Reference.Flag.INTERFACE)

    // class access flags
    aRef.flags.contains(Reference.Flag.PACKAGE_OR_HIGHER)
    bRef.flags.contains(Reference.Flag.PACKAGE_OR_HIGHER)

    // method refs
    assertMethod bRef, 'aMethod', '(Ljava/lang/String;)Ljava/lang/String;',
      Reference.Flag.PROTECTED_OR_HIGHER,
      Reference.Flag.NON_STATIC
    assertMethod bRef, 'aMethodWithPrimitives', '(Z)V',
      Reference.Flag.PROTECTED_OR_HIGHER,
      Reference.Flag.NON_STATIC
    assertMethod bRef, 'aStaticMethod', '()V',
      Reference.Flag.PROTECTED_OR_HIGHER,
      Reference.Flag.STATIC
    assertMethod bRef, 'aMethodWithArrays', '([Ljava/lang/String;)[Ljava/lang/Object;',
      Reference.Flag.PROTECTED_OR_HIGHER,
      Reference.Flag.NON_STATIC

    // field refs
    bRef.fields.isEmpty()
    aRef.fields.size() == 2
    assertField aRef, 'b', Reference.Flag.PACKAGE_OR_HIGHER, Reference.Flag.NON_STATIC
    assertField aRef, 'staticB', Reference.Flag.PACKAGE_OR_HIGHER, Reference.Flag.STATIC
  }

  def "protected ref test"() {
    setup:
    def references = ReferenceCreator.createReferencesFrom(MethodBodyAdvice.B2.name, this.class.classLoader)

    expect:
    assertMethod references[MethodBodyAdvice.B.name], 'protectedMethod', '()V',
      Reference.Flag.PROTECTED_OR_HIGHER,
      Reference.Flag.NON_STATIC
  }

  def "ldc creates references"() {
    setup:
    def references = ReferenceCreator.createReferencesFrom(LdcAdvice.name, this.class.classLoader)

    expect:
    references[MethodBodyAdvice.A.name] != null
  }

  def "instanceof creates references"() {
    setup:
    def references = ReferenceCreator.createReferencesFrom(TestClasses.InstanceofAdvice.name, this.class.classLoader)

    expect:
    references[MethodBodyAdvice.A.name] != null
  }

  // TODO: remove ignore when we drop java 7 support.
  @Ignore
  def "invokedynamic creates references"() {
    setup:
    def references = ReferenceCreator.createReferencesFrom(TestClasses.InDyAdvice.name, this.class.classLoader)

    expect:
    references['muzzle.TestClasses$MethodBodyAdvice$SomeImplementation'] != null
    references['muzzle.TestClasses$MethodBodyAdvice$B'] != null
  }

  def "should create references for helper classes"() {
    when:
    def references = ReferenceCreator.createReferencesFrom(HelperAdvice.name, this.class.classLoader)

    then:
    references.keySet() == [
      TestHelperClasses.Helper.name,
      TestHelperClasses.HelperSuperClass.name,
      TestHelperClasses.HelperInterface.name
    ] as Set

    with(references[TestHelperClasses.HelperSuperClass.name]) { helperSuperClass ->
      helperSuperClass.flags.contains(Reference.Flag.ABSTRACT)
      assertHelperSuperClassMethod(helperSuperClass, true)
      assertMethod helperSuperClass, 'finalMethod', '()Ljava/lang/String;',
        Reference.Flag.PUBLIC,
        Reference.Flag.NON_STATIC,
        Reference.Flag.FINAL
    }

    with(references[TestHelperClasses.HelperInterface.name]) { helperInterface ->
      helperInterface.flags.contains(Reference.Flag.ABSTRACT)
      assertHelperInterfaceMethod helperInterface, true
    }

    with(references[TestHelperClasses.Helper.name]) { helperClass ->
      helperClass.flags.contains(Reference.Flag.NON_FINAL)
      assertHelperSuperClassMethod helperClass, false
      assertHelperInterfaceMethod helperClass, false
    }
  }

  private static assertHelperSuperClassMethod(Reference reference, boolean isAbstract) {
    assertMethod reference, 'abstractMethod', '()I',
      Reference.Flag.PROTECTED,
      Reference.Flag.NON_STATIC,
      isAbstract ? Reference.Flag.ABSTRACT : Reference.Flag.NON_FINAL
  }

  private static assertHelperInterfaceMethod(Reference reference, boolean isAbstract) {
    assertMethod reference, 'foo', '()V',
      Reference.Flag.PUBLIC,
      Reference.Flag.NON_STATIC,
      isAbstract ? Reference.Flag.ABSTRACT : Reference.Flag.NON_FINAL
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
