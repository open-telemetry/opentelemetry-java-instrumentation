package datadog.trace.agent

import datadog.trace.agent.test.IntegrationTestUtils
import jvmbootstraptest.AgentLoadedChecker
import spock.lang.Specification

class AgentLoadedIntoBootstrapTest extends Specification {
  
  def "Agent loads in when separate jvm is launched"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(AgentLoadedChecker.getName()
      , "" as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }
}
