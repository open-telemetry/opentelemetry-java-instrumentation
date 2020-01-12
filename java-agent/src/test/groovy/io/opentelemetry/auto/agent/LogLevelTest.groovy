package io.opentelemetry.auto.agent

import io.opentelemetry.auto.agent.test.IntegrationTestUtils
import jvmbootstraptest.LogLevelChecker
import spock.lang.Specification

class LogLevelTest extends Specification {


  /* Priority: io.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel > opentelemetry.auto.trace.debug > OPENTELEMETRY_AUTO_TRACE_DEBUG
  1: INFO LOGS
  0: DEBUG Logs
   */

  def "opentelemetry.auto.trace.debug false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dopentelemetry.auto.trace.debug=false", "-Dopentelemetry.auto.trace.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 1
  }

  def "SLF4J DEBUG &&  opentelemetry.auto.trace.debug is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dopentelemetry.auto.trace.debug=false", "-Dio.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel=debug", "-Dopentelemetry.auto.trace.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }

  def "opentelemetry.auto.trace.debug is false && OPENTELEMETRY_AUTO_TRACE_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dopentelemetry.auto.trace.debug=false", "-Dopentelemetry.auto.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OPENTELEMETRY_AUTO_TRACE_DEBUG": "true"]
      , true) == 1
  }

  def "opentelemetry.auto.trace.debug is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dopentelemetry.auto.trace.debug=true", "-Dopentelemetry.auto.trace.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }


  def "OPENTELEMETRY_AUTO_TRACE_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dopentelemetry.auto.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OPENTELEMETRY_AUTO_TRACE_DEBUG": "true"]
      , true) == 0
  }

  def "opentelemetry.auto.trace.debug is true && OPENTELEMETRY_AUTO_TRACE_DEBUG is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dopentelemetry.auto.trace.debug=true", "-Dopentelemetry.auto.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OPENTELEMETRY_AUTO_TRACE_DEBUG": "false"]
      , true) == 0
  }


  def "SLF4J DEBUG && OPENTELEMETRY_AUTO_TRACE_DEBUG is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dio.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel=debug", "-Dopentelemetry.auto.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OPENTELEMETRY_AUTO_TRACE_DEBUG": "false"]
      , true) == 0
  }

  def "SLF4J INFO && OPENTELEMETRY_AUTO_TRACE_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dio.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel=info", "-Dopentelemetry.auto.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OPENTELEMETRY_AUTO_TRACE_DEBUG": "true"]
      , true) == 1
  }

}
