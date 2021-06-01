/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation

import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.instrumentation.api.config.ConfigBuilder
import spock.lang.Specification

class InstrumentationModuleTest extends Specification {

  def setup() {
    assert System.getenv().findAll { it.key.startsWith("OTEL_") }.isEmpty()
    assert System.getProperties().findAll { it.key.toString().startsWith("otel.") }.isEmpty()
  }

  def "default enabled"() {
    setup:
    def target = new TestInstrumentationModule(["test"])

    expect:
    target.enabled
  }

  def "default enabled override"() {
    expect:
    target.enabled == enabled

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
    Config.instance = new ConfigBuilder().readProperties([
      "otel.instrumentation.test.enabled": Boolean.toString(enabled)
    ]).build()
    def target = new TestInstrumentationModule(["test"]) {
      @Override
      protected boolean defaultEnabled() {
        return false
      }
    }

    expect:
    target.enabled == enabled

    cleanup:
    Config.instance = null

    where:
    enabled << [true, false]
  }

  static class TestInstrumentationModule extends InstrumentationModule {
    TestInstrumentationModule(List<String> instrumentationNames) {
      super(instrumentationNames)
    }

    @Override
    List<TypeInstrumentation> typeInstrumentations() {
      return []
    }
  }
}
