/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import io.opentelemetry.instrumentation.test.utils.ConfigUtils
import net.bytebuddy.agent.builder.AgentBuilder
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import spock.lang.Specification

class InstrumentationModuleTest extends Specification {

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  def setup() {
    assert System.getenv().findAll { it.key.startsWith("OTEL_") }.isEmpty()
    assert System.getProperties().findAll { it.key.toString().startsWith("otel.") }.isEmpty()
  }

  def "default enabled"() {
    setup:
    def target = new TestInstrumentationModule(["test"])
    target.instrument(new AgentBuilder.Default())

    expect:
    target.enabled
    target.applyCalled
  }

  def "default enabled override"() {
    setup:
    target.instrument(new AgentBuilder.Default())

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

  def "default disabled can override to enabled"() {
    setup:
    def previousConfig = ConfigUtils.updateConfig {
      it.setProperty("otel.instrumentation.test.enabled", "$enabled")
    }
    def target = new TestInstrumentationModule(["test"]) {
      @Override
      protected boolean defaultEnabled() {
        return false
      }
    }
    target.instrument(new AgentBuilder.Default())

    expect:
    target.enabled == enabled
    target.applyCalled == enabled

    cleanup:
    ConfigUtils.setConfig(previousConfig)

    where:
    enabled << [true, false]
  }

  def "configure default sys prop as #value"() {
    setup:
    def previousConfig = ConfigUtils.updateConfig {
      it.setProperty("otel.instrumentations.enabled", value)
    }
    def target = new TestInstrumentationModule(["test"])
    target.instrument(new AgentBuilder.Default())

    expect:
    target.enabled == enabled
    target.applyCalled == enabled

    cleanup:
    ConfigUtils.setConfig(previousConfig)

    where:
    value   | enabled
    "true"  | true
    "false" | false
    "asdf"  | false
  }

  def "configure sys prop enabled for #value when default is disabled"() {
    setup:
    def previousConfig = ConfigUtils.updateConfig {
      it.setProperty("otel.instrumentations.enabled", "false")
      it.setProperty("otel.instrumentation.${value}.enabled", "true")
    }
    def target = new TestInstrumentationModule([name, altName])
    target.instrument(new AgentBuilder.Default())

    expect:
    target.enabled == enabled
    target.applyCalled == enabled

    cleanup:
    ConfigUtils.setConfig(previousConfig)

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

    def getEnabled() {
      return super.enabled
    }

    @Override
    List<TypeInstrumentation> typeInstrumentations() {
      applyCalled = true
      return []
    }
  }
}
