package datadog.trace.agent.tooling

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestClass

class AgentInstallerTest extends AgentTestRunner {

  def "Exception in instrumentation is limited to that instrumentation"() {
    given:
    TestClass instance = new TestClass()

    when:
    String returnValue = instance.doSomething()

    then:
    returnValue == "overridden value"
  }
}
