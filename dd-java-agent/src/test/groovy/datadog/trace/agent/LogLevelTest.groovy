package datadog.trace.agent

import datadog.trace.agent.test.IntegrationTestUtils
import jvmbootstraptest.LogLevelChecker
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(30)
class LogLevelTest extends Specification {


  /* Priority: datadog.slf4j.simpleLogger.defaultLogLevel > dd.trace.debug > DD_TRACE_DEBUG
  1: INFO LOGS
  0: DEBUG Logs
   */

  def "dd.trace.debug false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Ddd.trace.debug=false", "-Ddd.jmxfetch.enabled=false", "-Ddd.trace.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 1
  }

  def "SLF4J DEBUG &&  dd.trace.debug is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Ddd.trace.debug=false", "-Ddatadog.slf4j.simpleLogger.defaultLogLevel=debug", "-Ddd.jmxfetch.enabled=false", "-Ddd.trace.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }

  def "dd.trace.debug is false && DD_TRACE_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Ddd.trace.debug=false", "-Ddd.jmxfetch.enabled=false", "-Ddd.trace.enabled=false"] as String[]
      , "" as String[]
      , ["DD_TRACE_DEBUG": "true"]
      , true) == 1
  }

  def "dd.trace.debug is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Ddd.trace.debug=true", "-Ddd.jmxfetch.enabled=false", "-Ddd.trace.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }


  def "DD_TRACE_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Ddd.jmxfetch.enabled=false", "-Ddd.trace.enabled=false"] as String[]
      , "" as String[]
      , ["DD_TRACE_DEBUG": "true"]
      , true) == 0
  }

  def "dd.trace.debug is true && DD_TRACE_DEBUG is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Ddd.trace.debug=true", "-Ddd.jmxfetch.enabled=false", "-Ddd.trace.enabled=false"] as String[]
      , "" as String[]
      , ["DD_TRACE_DEBUG": "false"]
      , true) == 0
  }


  def "SLF4J DEBUG && DD_TRACE_DEBUG is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Ddatadog.slf4j.simpleLogger.defaultLogLevel=debug", "-Ddd.jmxfetch.enabled=false", "-Ddd.trace.enabled=false"] as String[]
      , "" as String[]
      , ["DD_TRACE_DEBUG": "false"]
      , true) == 0
  }

  def "SLF4J INFO && DD_TRACE_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Ddatadog.slf4j.simpleLogger.defaultLogLevel=info", "-Ddd.jmxfetch.enabled=false", "-Ddd.trace.enabled=false"] as String[]
      , "" as String[]
      , ["DD_TRACE_DEBUG": "true"]
      , true) == 1
  }

}
