package muzzle

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.tooling.muzzle.Reference
import datadog.trace.agent.tooling.muzzle.ReferenceCreator
import static muzzle.TestClasses.*

class ReferenceCreatorTest extends AgentTestRunner {
  def "method body creates references"() {
    setup:
    Map<String, Reference> references = ReferenceCreator.createReferencesFrom(MethodBodyAdvice.getName(), this.getClass().getClassLoader())

    expect:
    references.get('java.lang.Object') != null
    references.get('muzzle.TestClasses$MethodBodyAdvice$A') != null
    references.get('muzzle.TestClasses$MethodBodyAdvice$B') != null
    references.get('muzzle.TestClasses$MethodBodyAdvice$SomeInterface') != null
    references.get('muzzle.TestClasses$MethodBodyAdvice$SomeImplementation') != null
    references.keySet().size() == 6

    Set<Reference.Method> bMethods = references.get('muzzle.TestClasses$MethodBodyAdvice$B').getMethods()
    bMethods.contains(method("aMethod", "(Ljava/lang/String;)Ljava/lang/String;"))
    bMethods.contains(method("aMethodWithPrimitives", "(Z)V"))
    bMethods.contains(method("aMethodWithArrays", "([Ljava/lang/String;)[Ljava/lang/Object;"))
  }

  private def method(String name, String descriptor) {
    return new Reference.Method(name, descriptor)
  }
}
