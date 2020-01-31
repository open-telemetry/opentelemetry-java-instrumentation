package io.opentelemetry.auto.test

import io.opentelemetry.auto.config.AgentConfig
import io.opentelemetry.auto.util.test.AgentSpecification

class ConfigTest extends AgentSpecification {
  def "test property config"() {
    System.setProperty("icecream", "yummy")
    System.setProperty("wolf", "bigbad")
    def envConfig = new AgentConfig.EnvironmentConfigProvider()
    def propConfig = new AgentConfig.SystemPropertyConfigProvider()
    def config = new AgentConfig.StackedConfigProvider(envConfig, propConfig)

    when:
    def result = config.get("icecream")

    then:
    result == "yummy"
  }

  def "test env config"() {
    def config = AgentConfig.getDefault()

    // Pick an environment variable at random and use that for testing
    def env = System.getenv()
    if (env.isEmpty()) {
      return
    }
    def key = env.keySet().iterator().next()
    def value = env[key]

    when:
    def result = config.get(key.toLowerCase())

    then:
    result == value
  }

  def "test prop override config"() {
    def config = AgentConfig.getDefault()

    // Pick an environment variable at random and use that for testing
    def env = System.getenv()
    if (env.isEmpty()) {
      return
    }
    def key = env.keySet().iterator().next()
    System.setProperty(key.toLowerCase(), "value is overridden")

    when:
    def result = config.get(key.toLowerCase())

    then:
    result == "value is overridden"
  }
}
