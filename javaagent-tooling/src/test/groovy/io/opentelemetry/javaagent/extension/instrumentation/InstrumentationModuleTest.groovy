/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation

import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.instrumentation.api.config.ConfigBuilder
import net.bytebuddy.agent.builder.AgentBuilder
import spock.lang.Specification

class InstrumentationModuleTest extends Specification {

  def setup() {
    assert System.getenv().findAll { it.key.startsWith("OTEL_") }.isEmpty()
    assert System.getProperties().findAll { it.key.toString().startsWith("otel.") }.isEmpty()
  }

  def "default enabled"() {
    setup:
    def target = new TestInstrumentationModule(["test"])
    target.extend(new AgentBuilder.Default())

    expect:
    target.enabled
    target.applyCalled
  }

  def "default enabled override"() {
    setup:
    target.extend(new AgentBuilder.Default())

    expect:
    target.enabled == enabled
    target.applyCalled == enabled

    where:
    enabled | target
    true    | new TestInstrumentationModule(["test"]) {
      @Override
      protected boolean defaultEnabled() {
        return true
      }
    }
    false   | new TestInstrumentationModule(["test"]) {
      @Override
      protected boolean defaultEnabled() {
        return false
      }
    }
  }

  def "default disabled can override to enabled #enabled"() {
    setup:
    Config.INSTANCE = new ConfigBuilder().readProperties([
      "otel.instrumentation.test.enabled": Boolean.toString(enabled)
    ]).build()
    def target = new TestInstrumentationModule(["test"]) {
      @Override
      protected boolean defaultEnabled() {
        return false
      }
    }
    target.extend(new AgentBuilder.Default())

    expect:
    target.enabled == enabled
    target.applyCalled == enabled

    cleanup:
    Config.INSTANCE = null

    where:
    enabled << [true, false]
  }

  static class TestInstrumentationModule extends InstrumentationModule {
    boolean applyCalled = false

    TestInstrumentationModule(List<String> instrumentationNames) {
      super(instrumentationNames)
    }

    @Override
    List<TypeInstrumentation> typeInstrumentations() {
      applyCalled = true
      return []
    }
  }
}
