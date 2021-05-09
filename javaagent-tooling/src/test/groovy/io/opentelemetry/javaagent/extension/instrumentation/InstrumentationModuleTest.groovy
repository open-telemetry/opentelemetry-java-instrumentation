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

  def "configure default sys prop as #value"() {
    setup:
    Config config = new ConfigBuilder().readProperties([
      "otel.instrumentation.common.default-enabled": String.valueOf(value)
    ]).build()

    expect:
    InstrumentationModule.initDefaultEnabled(config) == enabled

    where:
    value   | enabled
    "true"  | true
    "false" | false
    "asdf"  | false
  }

  def "configure sys prop enabled for #value when default is disabled"() {
    setup:
    Config config = new ConfigBuilder().readProperties([
      ("otel.instrumentation." + value + ".enabled"): "true"
    ]).build()

    expect:
    InstrumentationModule.initEnabled(config, [name, altName], false) == enabled

    where:
    value             | enabled | name          | altName
    "test"            | true    | "test"        | "asdf"
    "duplicate"       | true    | "duplicate"   | "duplicate"
    "bad"             | false   | "not"         | "valid"
    "altTest"         | true    | "asdf"        | "altTest"
    "dash-test"       | true    | "dash-test"   | "asdf"
    "underscore_test" | true    | "asdf"        | "underscore_test"
    "period.test"     | true    | "period.test" | "asdf"
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
