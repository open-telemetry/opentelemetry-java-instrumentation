package muzzle

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.tooling.muzzle.Reference
import datadog.trace.agent.tooling.muzzle.ReferenceCreator

import static muzzle.TestClasses.LdcAdvice
import static muzzle.TestClasses.MethodBodyAdvice

class ReferenceCreatorTest extends AgentTestRunner {
  def "method body creates references"() {
    setup:
    Map<String, Reference> references = ReferenceCreator.createReferencesFrom(MethodBodyAdvice.getName(), this.getClass().getClassLoader())

    expect:
    references.get('java.lang.Object') != null
    references.get('java.lang.String') != null
    references.get('muzzle.TestClasses$MethodBodyAdvice$A') != null
    references.get('muzzle.TestClasses$MethodBodyAdvice$B') != null
    references.get('muzzle.TestClasses$MethodBodyAdvice$SomeInterface') != null
    references.get('muzzle.TestClasses$MethodBodyAdvice$SomeImplementation') != null
    references.keySet().size() == 6

    // interface flags
    references.get('muzzle.TestClasses$MethodBodyAdvice$B').getFlags().contains(Reference.Flag.NON_INTERFACE)
    references.get('muzzle.TestClasses$MethodBodyAdvice$SomeInterface').getFlags().contains(Reference.Flag.INTERFACE)

    // class access flags
    references.get('java.lang.Object').getFlags().contains(Reference.Flag.PUBLIC)
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
