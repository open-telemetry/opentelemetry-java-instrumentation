package datadog.trace.agent.tooling

import net.bytebuddy.agent.builder.AgentBuilder
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import spock.lang.Specification
import spock.lang.Unroll

class ConfigurableInstrumenterTest extends Specification {
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
    def target = new TestConfigurableInstrumenter("test")
    target.instrument(null)

    expect:
    target.enabled
    target.applyCalled
  }

  def "default enabled override"() {
    expect:
    target.instrument(null) == null
    target.enabled == enabled
    target.applyCalled == enabled

    where:
    enabled | target
    true    | new TestConfigurableInstrumenter("test") {
      @Override
      protected boolean defaultEnabled() {
        return true
      }
    }
    false   | new TestConfigurableInstrumenter("test") {
      @Override
      protected boolean defaultEnabled() {
        return false
      }
    }
  }

  def "default disabled can override to enabled"() {
    setup:
    System.setProperty("dd.integration.test.enabled", "$enabled")
    def target = new TestConfigurableInstrumenter("test") {
      @Override
      protected boolean defaultEnabled() {
        return false
      }
    }

    expect:
    target.instrument(null) == null
    target.enabled == enabled
    target.applyCalled == enabled

    where:
    enabled << [true, false]
  }

  @Unroll
  def "configure default sys prop as #value"() {
    setup:
    System.setProperty("dd.integrations.enabled", value)
    def target = new TestConfigurableInstrumenter("test")

    expect:
    target.instrument(null) == null
    target.enabled == enabled
    target.applyCalled == enabled

    where:
    value   | enabled
    "true"  | true
    "false" | false
    "asdf"  | false
  }

  @Unroll
  def "configure default env var as #value"() {
    setup:
    environmentVariables.set("DD_INTEGRATIONS_ENABLED", value)
    def target = new TestConfigurableInstrumenter("test")

    expect:
    target.instrument(null) == null
    target.enabled == enabled
    target.applyCalled == enabled

    where:
    value   | enabled
    "true"  | true
    "false" | false
    "asdf"  | false
  }

  @Unroll
  def "configure sys prop enabled for #value when default is disabled"() {
    setup:
    System.setProperty("dd.integrations.enabled", "false")
    System.setProperty("dd.integration.${value}.enabled", "true")
    def target = new TestConfigurableInstrumenter(name, altName)

    expect:
    target.instrument(null) == null
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

  @Unroll
  def "configure env var enabled for #value when default is disabled"() {
    setup:
    environmentVariables.set("DD_INTEGRATIONS_ENABLED", "false")
    environmentVariables.set("DD_INTEGRATION_${value}_ENABLED", "true")
    def target = new TestConfigurableInstrumenter(name, altName)

    expect:
    System.getenv("DD_INTEGRATION_${value}_ENABLED") == "true"
    target.instrument(null) == null
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

  class TestConfigurableInstrumenter extends Instrumenter.Configurable {
    boolean applyCalled = false

    TestConfigurableInstrumenter(
      String instrumentationName) {
      super(instrumentationName)
    }

    TestConfigurableInstrumenter(
      String instrumentationName, String additionalName) {
      super(instrumentationName, [additionalName])
    }

    @Override
    protected AgentBuilder apply(AgentBuilder agentBuilder) {
      applyCalled = true
      return null
    }

    def getEnabled() {
      return super.enabled
    }
  }
}
