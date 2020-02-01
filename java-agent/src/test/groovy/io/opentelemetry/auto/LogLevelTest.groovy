package io.opentelemetry.auto

import io.opentelemetry.auto.test.IntegrationTestUtils
import jvmbootstraptest.LogLevelChecker
import spock.lang.Specification

class LogLevelTest extends Specification {


  /* Priority: io.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel > opentelemetry.auto.trace.debug > OTA_TRACE_DEBUG
  1: INFO LOGS
  0: DEBUG Logs
   */

  def "ota.trace.debug false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dota.trace.debug=false", "-Dota.trace.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 1
  }

  def "SLF4J DEBUG && ota.trace.debug is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dota.trace.debug=false", "-Dio.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel=debug", "-Dota.trace.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }

  def "ota.trace.debug is false && OTA_TRACE_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dota.trace.debug=false", "-Dota.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OTA_TRACE_DEBUG": "true"]
      , true) == 1
  }

  def "ota.trace.debug is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dota.trace.debug=true", "-Dota.trace.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }


  def "OTA_TRACE_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dota.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OTA_TRACE_DEBUG": "true"]
      , true) == 0
  }

  def "ota.trace.debug is true && OTA_TRACE_DEBUG is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dota.trace.debug=true", "-Dota.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OTA_TRACE_DEBUG": "false"]
      , true) == 0
  }


  def "SLF4J DEBUG && OTA_TRACE_DEBUG is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dio.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel=debug", "-Dota.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OTA_TRACE_DEBUG": "false"]
      , true) == 0
  }

  def "SLF4J INFO && OTA_TRACE_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dio.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel=info", "-Dota.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OTA_TRACE_DEBUG": "true"]
      , true) == 1
  }

}
