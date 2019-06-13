package datadog.trace.agent.test

import datadog.trace.agent.test.utils.ConfigUtils
import datadog.trace.agent.tooling.Instrumenter
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.matcher.ElementMatcher
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import spock.lang.Specification

class DefaultInstrumenterTest extends Specification {
  static {
    ConfigUtils.makeConfigInstanceModifiable()
  }

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  def setup() {
    assert System.getenv().findAll { it.key.startsWith("DD_") }.isEmpty()
    assert System.getProperties().findAll { it.key.toString().startsWith("dd.") }.isEmpty()
  }

  def "default enabled"() {
    setup:
    def target = new TestDefaultInstrumenter("test")
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
    true    | new TestDefaultInstrumenter("test") {
      @Override
      protected boolean defaultEnabled() {
        return true
      }
    }
    false   | new TestDefaultInstrumenter("test") {
      @Override
      protected boolean defaultEnabled() {
        return false
      }
    }
  }

  def "default disabled can override to enabled"() {
    setup:
    System.setProperty("dd.integration.test.enabled", "$enabled")
    def target = new TestDefaultInstrumenter("test") {
      @Override
      protected boolean defaultEnabled() {
        return false
      }
    }
    target.instrument(new AgentBuilder.Default())

    expect:
    target.enabled == enabled
    target.applyCalled == enabled

    where:
    enabled << [true, false]
  }

  def "configure default sys prop as #value"() {
    setup:
    ConfigUtils.updateConfig {
      System.setProperty("dd.integrations.enabled", value)
    }
    def target = new TestDefaultInstrumenter("test")
    target.instrument(new AgentBuilder.Default())

    expect:
    target.enabled == enabled
    target.applyCalled == enabled

    where:
    value   | enabled
    "true"  | true
    "false" | false
    "asdf"  | false
  }

  def "configure default env var as #value"() {
    setup:
    environmentVariables.set("DD_INTEGRATIONS_ENABLED", value)
    ConfigUtils.resetConfig()
    def target = new TestDefaultInstrumenter("test")
    target.instrument(new AgentBuilder.Default())

    expect:
    target.enabled == enabled
    target.applyCalled == enabled

    where:
    value   | enabled
    "true"  | true
    "false" | false
    "asdf"  | false
  }

  def "configure sys prop enabled for #value when default is disabled"() {
    setup:
    System.setProperty("dd.integrations.enabled", "false")
    System.setProperty("dd.integration.${value}.enabled", "true")
    def target = new TestDefaultInstrumenter(name, altName)
    target.instrument(new AgentBuilder.Default())

    expect:
    target.enabled == enabled
    target.applyCalled == enabled

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

  def "configure env var enabled for #value when default is disabled"() {
    setup:
    environmentVariables.set("DD_INTEGRATIONS_ENABLED", "false")
    environmentVariables.set("DD_INTEGRATION_${value}_ENABLED", "true")
    def target = new TestDefaultInstrumenter(name, altName)
    target.instrument(new AgentBuilder.Default())

    expect:
    System.getenv("DD_INTEGRATION_${value}_ENABLED") == "true"
    target.enabled == enabled
    target.applyCalled == enabled

    where:
    value             | enabled | name          | altName
    "TEST"            | true    | "test"        | "asdf"
    "DUPLICATE"       | true    | "duplicate"   | "duplicate"
    "BAD"             | false   | "not"         | "valid"
    "ALTTEST"         | true    | "asdf"        | "altTest"
    "DASH_TEST"       | true    | "dash-test"   | "asdf"
    "UNDERSCORE_TEST" | true    | "asdf"        | "underscore_test"
    "PERIOD_TEST"     | true    | "period.test" | "asdf"
  }

  class TestDefaultInstrumenter extends Instrumenter.Default {
    boolean applyCalled = false

    TestDefaultInstrumenter(
      String instrumentationName) {
      super(instrumentationName)
    }

    TestDefaultInstrumenter(
      String instrumentationName, String additionalName) {
      super(instrumentationName, [additionalName])
    }

    def getEnabled() {
      return super.enabled
    }

    @Override
    ElementMatcher<? super TypeDescription> typeMatcher() {
      applyCalled = true
      return new ElementMatcher() {
        @Override
        boolean matches(Object target) {
          return false
        }
      }
    }

    @Override
    Map<ElementMatcher, String> transformers() {
      return Collections.emptyMap()
    }
  }
}
