/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto

import io.opentelemetry.auto.test.IntegrationTestUtils
import jvmbootstraptest.LogLevelChecker
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(30)
class LogLevelTest extends Specification {


  /* Priority: io.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel > opentelemetry.auto.trace.debug > OTEL_TRACE_DEBUG
  1: INFO LOGS
  0: DEBUG Logs
   */

  def "otel.trace.debug false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dotel.trace.debug=false", "-Dotel.trace.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 1
  }

  def "SLF4J DEBUG && otel.trace.debug is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dotel.trace.debug=false", "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=debug", "-Dotel.trace.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }

  def "otel.trace.debug is false && OTEL_TRACE_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dotel.trace.debug=false", "-Dotel.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OTEL_TRACE_DEBUG": "true"]
      , true) == 1
  }

  def "otel.trace.debug is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dotel.trace.debug=true", "-Dotel.trace.enabled=false"] as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }


  def "OTEL_TRACE_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dotel.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OTEL_TRACE_DEBUG": "true"]
      , true) == 0
  }

  def "otel.trace.debug is true && OTEL_TRACE_DEBUG is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dotel.trace.debug=true", "-Dotel.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OTEL_TRACE_DEBUG": "false"]
      , true) == 0
  }


  def "SLF4J DEBUG && OTEL_TRACE_DEBUG is false"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=debug", "-Dotel.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OTEL_TRACE_DEBUG": "false"]
      , true) == 0
  }

  def "SLF4J INFO && OTEL_TRACE_DEBUG is true"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(LogLevelChecker.getName()
      , ["-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=info", "-Dotel.trace.enabled=false"] as String[]
      , "" as String[]
      , ["OTEL_TRACE_DEBUG": "true"]
      , true) == 1
  }

}
