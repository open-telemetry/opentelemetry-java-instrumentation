/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.extension.noopapi.NoopOpenTelemetry
import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.javaagent.bootstrap.AgentLogEmitterProvider
import spock.lang.Specification

class OpenTelemetryInstallerTest extends Specification {

  void setup() {
    GlobalOpenTelemetry.resetForTest()
    AgentLogEmitterProvider.resetForTest()
  }

  void cleanup() {
    GlobalOpenTelemetry.resetForTest()
    AgentLogEmitterProvider.resetForTest()
  }

  def "should initialize GlobalOpenTelemetry"() {
    when:
    def otelInstaller = OpenTelemetryInstaller.installOpenTelemetrySdk(Config.builder().build())

    then:
    otelInstaller != null
    GlobalOpenTelemetry.getTracerProvider() != NoopOpenTelemetry.getInstance().getTracerProvider()
  }

}
