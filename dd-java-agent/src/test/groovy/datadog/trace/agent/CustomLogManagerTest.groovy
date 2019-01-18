package datadog.trace.agent

import datadog.trace.agent.test.IntegrationTestUtils
import jvmbootstraptest.LogManagerSetter
import spock.lang.Shared
import spock.lang.Specification

import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean

class CustomLogManagerTest extends Specification {
  // Run all tests using forked jvm because groovy has already set the global log manager

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

  def "jmxfetch starts up in premain with no custom log manager set"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogManagerSetter.getName()
      , [agentArg, "-Ddd.jmxfetch.enabled=true", "-Ddd.jmxfetch.refresh-beans-period=1", "-Ddatadog.slf4j.simpleLogger.defaultLogLevel=off"] as String[]
      , "" as String[]
      , true) == 0
  }

  def "jmxfetch startup is delayed with java.util.logging.manager sysprop"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogManagerSetter.getName()
      , [agentArg, "-Ddd.jmxfetch.enabled=true", "-Ddd.jmxfetch.refresh-beans-period=1", "-Ddatadog.slf4j.simpleLogger.defaultLogLevel=off", "-Djava.util.logging.manager=jvmbootstraptest.CustomLogManager"] as String[]
      , "" as String[]
      , true) == 0
  }

  def "jmxfetch startup delayed with tracer custom log manager setting"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogManagerSetter.getName()
      , [agentArg, "-Ddd.jmxfetch.enabled=true", "-Ddd.jmxfetch.refresh-beans-period=1", "-Ddatadog.slf4j.simpleLogger.defaultLogLevel=off", "-Ddd.app.customlogmanager=true"] as String[]
      , "" as String[]
      , true) == 0
  }
}
