package datadog.trace.agent

import datadog.trace.agent.test.IntegrationTestUtils
import jvmbootstraptest.AgentLoadedChecker
import spock.lang.Shared
import spock.lang.Specification

import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean

class AgentLoadedIntoBootstrapTest extends Specification {
  @Shared
  private agentArg

  def setupSpec() {
    final RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean()
    for (String arg : runtimeMxBean.getInputArguments()) {
      if (arg.startsWith("-javaagent")) {
        agentArg = arg
        break
      }
    }
    assert agentArg != null
  }

  def "Agent loads in when separate jvm is launched"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(AgentLoadedChecker.getName()
      , [agentArg] as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }
}
