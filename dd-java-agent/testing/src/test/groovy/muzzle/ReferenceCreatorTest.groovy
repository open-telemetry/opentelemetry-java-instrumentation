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
    references.keySet().size() == 5
  }

}
