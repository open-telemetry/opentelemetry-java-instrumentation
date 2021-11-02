/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.extension.noopapi.NoopOpenTelemetry
import io.opentelemetry.instrumentation.api.config.Config
import spock.lang.Specification

class OpenTelemetryInstallerTest extends Specification {


  void setup() {
    GlobalOpenTelemetry.resetForTest()
  }

  void cleanup() {
    GlobalOpenTelemetry.resetForTest()
  }


  def "should initialize noop"() {

    given:
    def config = Config.builder()
      .readProperties([
        (OpenTelemetryInstaller.JAVAAGENT_NOOP_CONFIG)   : "true",
        (OpenTelemetryInstaller.JAVAAGENT_ENABLED_CONFIG): "true"
      ])
      .build()

    when:
    def otelInstaller = new OpenTelemetryInstaller()
    otelInstaller.beforeAgent(config)

    then:
    GlobalOpenTelemetry.getTracerProvider() == NoopOpenTelemetry.getInstance().getTracerProvider()
  }

  def "should NOT initialize noop"() {

    given:
    def config = Config.builder()
      .readProperties([
        (OpenTelemetryInstaller.JAVAAGENT_NOOP_CONFIG)   : "true",
        (OpenTelemetryInstaller.JAVAAGENT_ENABLED_CONFIG): "false"
      ])
      .build()

    when:
    def otelInstaller = new OpenTelemetryInstaller()
    otelInstaller.beforeAgent(config)

    then:
    GlobalOpenTelemetry.getTracerProvider() != NoopOpenTelemetry.getInstance().getTracerProvider()
  }

}
