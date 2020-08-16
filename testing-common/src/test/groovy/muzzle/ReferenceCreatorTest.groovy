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

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.javaagent.tooling.muzzle.Reference
import io.opentelemetry.javaagent.tooling.muzzle.ReferenceCreator

import static muzzle.TestClasses.LdcAdvice
import static muzzle.TestClasses.MethodBodyAdvice

class ReferenceCreatorTest extends AgentTestRunner {
  def "method body creates references"() {
    setup:
    Map<String, Reference> references = ReferenceCreator.createReferencesFrom(MethodBodyAdvice.getName(), this.getClass().getClassLoader())

    expect:
    references.get('muzzle.TestClasses$MethodBodyAdvice$A') != null
    references.get('muzzle.TestClasses$MethodBodyAdvice$B') != null
    references.get('muzzle.TestClasses$MethodBodyAdvice$SomeInterface') != null
    references.get('muzzle.TestClasses$MethodBodyAdvice$SomeImplementation') != null
    references.keySet().size() == 4

    // interface flags
    references.get('muzzle.TestClasses$MethodBodyAdvice$B').getFlags().contains(Reference.Flag.NON_INTERFACE)
    references.get('muzzle.TestClasses$MethodBodyAdvice$SomeInterface').getFlags().contains(Reference.Flag.INTERFACE)

    // class access flags
    references.get('muzzle.TestClasses$MethodBodyAdvice$A').getFlags().contains(Reference.Flag.PACKAGE_OR_HIGHER)
    references.get('muzzle.TestClasses$MethodBodyAdvice$B').getFlags().contains(Reference.Flag.PACKAGE_OR_HIGHER)

    // method refs
    Set<Reference.Method> bMethods = references.get('muzzle.TestClasses$MethodBodyAdvice$B').getMethods()
    findMethod(bMethods, "aMethod", "(Ljava/lang/String;)Ljava/lang/String;") != null
    findMethod(bMethods, "aMethodWithPrimitives", "(Z)V") != null
    findMethod(bMethods, "aStaticMethod", "()V") != null
    findMethod(bMethods, "aMethodWithArrays", "([Ljava/lang/String;)[Ljava/lang/Object;") != null

    findMethod(bMethods, "aMethod", "(Ljava/lang/String;)Ljava/lang/String;").getFlags().contains(Reference.Flag.NON_STATIC)
    findMethod(bMethods, "aStaticMethod", "()V").getFlags().contains(Reference.Flag.STATIC)

    // field refs
    references.get('muzzle.TestClasses$MethodBodyAdvice$B').getFields().isEmpty()
    Set<Reference.Field> aFieldRefs = references.get('muzzle.TestClasses$MethodBodyAdvice$A').getFields()
    findField(aFieldRefs, "b").getFlags().contains(Reference.Flag.PACKAGE_OR_HIGHER)
    findField(aFieldRefs, "b").getFlags().contains(Reference.Flag.NON_STATIC)
    findField(aFieldRefs, "staticB").getFlags().contains(Reference.Flag.PACKAGE_OR_HIGHER)
    findField(aFieldRefs, "staticB").getFlags().contains(Reference.Flag.STATIC)
    aFieldRefs.size() == 2
  }

  def "protected ref test"() {
    setup:
    Map<String, Reference> references = ReferenceCreator.createReferencesFrom(MethodBodyAdvice.B2.getName(), this.getClass().getClassLoader())

    expect:
    Set<Reference.Method> bMethods = references.get('muzzle.TestClasses$MethodBodyAdvice$B').getMethods()
    findMethod(bMethods, "protectedMethod", "()V") != null
    findMethod(bMethods, "protectedMethod", "()V").getFlags().contains(Reference.Flag.PROTECTED_OR_HIGHER)
  }

  def "ldc creates references"() {
    setup:
    Map<String, Reference> references = ReferenceCreator.createReferencesFrom(LdcAdvice.getName(), this.getClass().getClassLoader())

    expect:
    references.get('muzzle.TestClasses$MethodBodyAdvice$A') != null
  }

  private static Reference.Method findMethod(Set<Reference.Method> methods, String methodName, String methodDesc) {
    for (Reference.Method method : methods) {
      if (method == new Reference.Method(methodName, methodDesc)) {
        return method
      }
    }
    return null
  }

  private static Reference.Field findField(Set<Reference.Field> fields, String fieldName) {
    for (Reference.Field field : fields) {
      if (field.getName().equals(fieldName)) {
        return field
      }
    }
    return null
  }
}
