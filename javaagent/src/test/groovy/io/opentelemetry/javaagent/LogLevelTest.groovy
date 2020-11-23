/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent

import jvmbootstraptest.LogLevelChecker
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(30)
class LogLevelTest extends Specification {


  /* Priority: io.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel > opentelemetry.javaagent.debug > OTEL_JAVAAGENT_DEBUG
  1: INFO LOGS
  0: DEBUG Logs
   */

  def "otel.javaagent.debug false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dotel.javaagent.debug=false", "-Dotel.javaagent.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 1
  }

  def "SLF4J DEBUG && otel.javaagent.debug is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dotel.javaagent.debug=false", "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=debug", "-Dotel.javaagent.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }

  def "otel.javaagent.debug is false && OTEL_JAVAAGENT_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dotel.javaagent.debug=false", "-Dotel.javaagent.enabled=false"] as String[]
      , "" as String[]
      , ["OTEL_JAVAAGENT_DEBUG": "true"]
      , true) == 1
  }

  def "otel.javaagent.debug is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dotel.javaagent.debug=true", "-Dotel.javaagent.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }


  def "OTEL_JAVAAGENT_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dotel.javaagent.enabled=false"] as String[]
      , "" as String[]
      , ["OTEL_JAVAAGENT_DEBUG": "true"]
      , true) == 0
  }

  def "otel.javaagent.debug is true && OTEL_JAVAAGENT_DEBUG is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dotel.javaagent.debug=true", "-Dotel.javaagent.enabled=false"] as String[]
      , "" as String[]
      , ["OTEL_JAVAAGENT_DEBUG": "false"]
      , true) == 0
  }


  def "SLF4J DEBUG && OTEL_JAVAAGENT_DEBUG is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=debug", "-Dotel.javaagent.enabled=false"] as String[]
      , "" as String[]
      , ["OTEL_JAVAAGENT_DEBUG": "false"]
      , true) == 0
  }

  def "SLF4J INFO && OTEL_JAVAAGENT_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=info", "-Dotel.javaagent.enabled=false"] as String[]
      , "" as String[]
      , ["OTEL_JAVAAGENT_DEBUG": "true"]
      , true) == 1
  }

}
