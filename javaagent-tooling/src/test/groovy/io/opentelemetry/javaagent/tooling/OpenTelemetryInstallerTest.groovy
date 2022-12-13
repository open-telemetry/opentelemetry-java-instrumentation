/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.logs.GlobalLoggerProvider
import spock.lang.Specification

class OpenTelemetryInstallerTest extends Specification {

  void setup() {
    GlobalOpenTelemetry.resetForTest()
    GlobalLoggerProvider.resetForTest()
  }

  void cleanup() {
    GlobalOpenTelemetry.resetForTest()
    GlobalLoggerProvider.resetForTest()
  }

  def "should initialize GlobalOpenTelemetry"() {
    when:
    def otelInstaller = OpenTelemetryInstaller.installOpenTelemetrySdk(OpenTelemetryInstaller.classLoader)

    then:
    otelInstaller != null
    GlobalOpenTelemetry.getTracerProvider() != OpenTelemetry.noop().getTracerProvider()
  }

}
