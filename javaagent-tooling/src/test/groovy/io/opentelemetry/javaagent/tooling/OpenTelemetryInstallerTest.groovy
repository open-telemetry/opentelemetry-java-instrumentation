/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.extension.noopapi.NoopOpenTelemetry;
import spock.lang.Specification

class OpenTelemetryInstallerTest extends Specification {

  static final String JAVAAGENT_ENABLED_CONFIG = "otel.javaagent.enabled";
  static final String JAVAAGENT_NOOP_CONFIG = "otel.javaagent.experimental.sdk.noop";

  void setup(){
    GlobalOpenTelemetry.resetForTest()
  }

  void cleanup(){
    GlobalOpenTelemetry.resetForTest()
  }


  def "should initialize noop"(){

    given:
    def config = Mock(Config)
    config.getBooleanProperty(JAVAAGENT_NOOP_CONFIG, false) >> true
    config.getBooleanProperty(JAVAAGENT_ENABLED_CONFIG, true) >> true
    config.getAllProperties() >> ["otel.javaagent.enabled":"true", "otel.javaagent.experimental.sdk.noop":"true"]

    when:
    OpenTelemetryInstaller.installAgentTracer(config)

    then:
    GlobalOpenTelemetry.getTracerProvider() == NoopOpenTelemetry.getInstance().getTracerProvider()
  }

  def "should NOT initialize noop"(){

    given:
    def config = Mock(Config)
    config.getBooleanProperty(JAVAAGENT_NOOP_CONFIG, false) >> false
    config.getBooleanProperty(JAVAAGENT_ENABLED_CONFIG, true) >> true
    config.getAllProperties() >> ["otel.javaagent.enabled":"true", "otel.javaagent.experimental.sdk.noop":"false"]

    when:
    OpenTelemetryInstaller.installAgentTracer(config)

    then:
    GlobalOpenTelemetry.getTracerProvider() != NoopOpenTelemetry.getInstance().getTracerProvider()
  }

}
