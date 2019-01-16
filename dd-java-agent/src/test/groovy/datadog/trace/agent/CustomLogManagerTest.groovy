package datadog.trace.agent

import datadog.trace.agent.test.IntegrationTestUtils
import jvmbootstraptest.LogManagerSetter
import spock.lang.Shared
import spock.lang.Specification

import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean

class CustomLogManagerTest extends Specification {
  // Tests using forked jvm because groovy has already set the global log manager
  @Shared
  private def agentArg

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

  def "jmxfetch starts up in premain when no custom log manager is set"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogManagerSetter.getName()
      , [agentArg, "-Ddd.jmxfetch.enabled=true", "-Ddd.jmxfetch.refresh-beans-period=1", "-Ddatadog.slf4j.simpleLogger.defaultLogLevel=off"] as String[]
      , "" as String[]
      , true) == 0
  }

  def "jmxfetch startup is delayed when java.util.logging.manager sysprop is present"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogManagerSetter.getName()
      , [agentArg, "-Ddd.jmxfetch.enabled=true", "-Ddd.jmxfetch.refresh-beans-period=1", "-Ddatadog.slf4j.simpleLogger.defaultLogLevel=off", "-Djava.util.logging.manager=jvmbootstraptest.CustomLogManager"] as String[]
      , "" as String[]
      , true) == 0
  }

  def "jmxfetch startup is delayed when tracer custom log manager setting is present"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogManagerSetter.getName()
      , ["-javaagent:" + customAgent.getPath(), agentArg, "-Ddd.jmxfetch.enabled=true", "-Ddd.jmxfetch.refresh-beans-period=1", "-Ddatadog.slf4j.simpleLogger.defaultLogLevel=off", "-Ddd.app.customlogmanager=true"] as String[]
      , "" as String[]
      , true) == 0
  }
}
