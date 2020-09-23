/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.tooling.config

import io.opentelemetry.auto.util.test.AgentSpecification
import io.opentelemetry.instrumentation.api.config.Config
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties

class ConfigBuilderTest extends AgentSpecification {
  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  def "should use defaults"() {
    when:
    def config = new ConfigBuilder()
      .readPropertiesFromAllSources(new Properties(), new Properties())
      .build()

    then:
    config.exporterJar == null
    config.exporter == Config.DEFAULT_EXPORTER
    config.propagators.empty
    config.traceEnabled
    config.integrationsEnabled
    config.excludedClasses.empty
    config.runtimeContextFieldInjection
    config.traceAnnotations == null
    config.traceMethods == null
    config.traceAnnotatedMethodsExclude == null
    !config.traceExecutorsAll
    config.traceExecutors.empty
    config.sqlNormalizerEnabled
    config.kafkaClientPropagationEnabled
    !config.hystrixTagsEnabled
    config.endpointPeerServiceMapping.isEmpty()
  }

  def "should use configuration from SPI (takes precedence over defaults)"() {
    given:
    def spiConfiguration = new Properties()
    spiConfiguration.put(ConfigBuilder.EXPORTER, "zipkin")
    spiConfiguration.put(ConfigBuilder.HYSTRIX_TAGS_ENABLED, "true")
    spiConfiguration.put(ConfigBuilder.ENDPOINT_PEER_SERVICE_MAPPING, "1.2.3.4=cats,dogs.com=dogs")
    spiConfiguration.put(ConfigBuilder.TRACE_METHODS, "mypackage.MyClass[myMethod]")

    when:
    def config = new ConfigBuilder()
      .readPropertiesFromAllSources(spiConfiguration, new Properties())
      .build()

    then:
    config.exporter == "zipkin"
    config.hystrixTagsEnabled
    config.endpointPeerServiceMapping == ["1.2.3.4": "cats", "dogs.com": "dogs"]
    config.traceMethods == "mypackage.MyClass[myMethod]"
  }

  def "should use configuration file properties (takes precedence over SPI)"() {
    given:
    def spiConfiguration = new Properties()
    spiConfiguration.put(ConfigBuilder.EXPORTER, "zipkin")
    spiConfiguration.put(ConfigBuilder.HYSTRIX_TAGS_ENABLED, "true")
    spiConfiguration.put(ConfigBuilder.ENDPOINT_PEER_SERVICE_MAPPING, "1.2.3.4=cats,dogs.com=dogs")
    spiConfiguration.put(ConfigBuilder.TRACE_METHODS, "mypackage.MyClass[myMethod]")

    def configurationFile = new Properties()
    configurationFile.put(ConfigBuilder.EXPORTER, "logging")
    configurationFile.put(ConfigBuilder.TRACE_METHODS, "mypackage2.MyClass2[myMethod2]")

    when:
    def config = new ConfigBuilder()
      .readPropertiesFromAllSources(spiConfiguration, configurationFile)
      .build()

    then:
    config.exporter == "logging"
    config.hystrixTagsEnabled
    config.endpointPeerServiceMapping == ["1.2.3.4": "cats", "dogs.com": "dogs"]
    config.traceMethods == "mypackage2.MyClass2[myMethod2]"
  }

  def "should use environment variables (takes precedence over configuration file)"() {
    given:
    def spiConfiguration = new Properties()
    spiConfiguration.put(ConfigBuilder.EXPORTER, "zipkin")
    spiConfiguration.put(ConfigBuilder.HYSTRIX_TAGS_ENABLED, "true")
    spiConfiguration.put(ConfigBuilder.ENDPOINT_PEER_SERVICE_MAPPING, "1.2.3.4=cats,dogs.com=dogs")
    spiConfiguration.put(ConfigBuilder.TRACE_METHODS, "mypackage.MyClass[myMethod]")

    def configurationFile = new Properties()
    configurationFile.put(ConfigBuilder.EXPORTER, "logging")
    configurationFile.put(ConfigBuilder.TRACE_METHODS, "mypackage2.MyClass2[myMethod2]")

    environmentVariables.set("OTEL_EXPORTER", "jaeger")
    environmentVariables.set("OTEL_ENDPOINT_PEER_SERVICE_MAPPING", "4.2.4.2=elephants.com")

    when:
    def config = new ConfigBuilder()
      .readPropertiesFromAllSources(spiConfiguration, configurationFile)
      .build()

    then:
    config.exporter == "jaeger"
    config.hystrixTagsEnabled
    config.endpointPeerServiceMapping == ["4.2.4.2": "elephants.com"]
    config.traceMethods == "mypackage2.MyClass2[myMethod2]"
  }

  def "should use system properties (takes precedence over environment variables)"() {
    given:
    def spiConfiguration = new Properties()
    spiConfiguration.put(ConfigBuilder.EXPORTER, "zipkin")
    spiConfiguration.put(ConfigBuilder.HYSTRIX_TAGS_ENABLED, "true")
    spiConfiguration.put(ConfigBuilder.ENDPOINT_PEER_SERVICE_MAPPING, "1.2.3.4=cats,dogs.com=dogs")
    spiConfiguration.put(ConfigBuilder.TRACE_METHODS, "mypackage.MyClass[myMethod]")

    def configurationFile = new Properties()
    configurationFile.put(ConfigBuilder.EXPORTER, "logging")
    configurationFile.put(ConfigBuilder.TRACE_METHODS, "mypackage2.MyClass2[myMethod2]")

    environmentVariables.set("OTEL_EXPORTER", "jaeger")
    environmentVariables.set("OTEL_ENDPOINT_PEER_SERVICE_MAPPING", "4.2.4.2=elephants.com")

    System.setProperty(ConfigBuilder.EXPORTER, "otlp")
    System.setProperty(ConfigBuilder.TRACE_METHODS, "mypackage3.MyClass3[myMethod3]")

    when:
    def config = new ConfigBuilder()
      .readPropertiesFromAllSources(spiConfiguration, configurationFile)
      .build()

    then:
    config.exporter == "otlp"
    config.hystrixTagsEnabled
    config.endpointPeerServiceMapping == ["4.2.4.2": "elephants.com"]
    config.traceMethods == "mypackage3.MyClass3[myMethod3]"
  }

  def "should use defaults in case of parsing failure"() {
    given:
    System.setProperty(ConfigBuilder.ENDPOINT_PEER_SERVICE_MAPPING, "not a map")

    when:
    def config = new ConfigBuilder()
      .readPropertiesFromAllSources(new Properties(), new Properties())
      .build()

    then:
    config.endpointPeerServiceMapping.isEmpty()
  }

  def "verify integration config"() {
    setup:
    environmentVariables.set("OTEL_INTEGRATION_ORDER_ENABLED", "false")
    environmentVariables.set("OTEL_INTEGRATION_TEST_ENV_ENABLED", "true")
    environmentVariables.set("OTEL_INTEGRATION_DISABLED_ENV_ENABLED", "false")

    System.setProperty("otel.integration.order.enabled", "true")
    System.setProperty("otel.integration.test-prop.enabled", "true")
    System.setProperty("otel.integration.disabled-prop.enabled", "false")

    def config = new ConfigBuilder()
      .readPropertiesFromAllSources(new Properties(), new Properties())
      .build()

    expect:
    config.isIntegrationEnabled(integrationNames, defaultEnabled) == expected

    where:
    names                          | defaultEnabled | expected
    []                             | true           | true
    []                             | false          | false
    ["invalid"]                    | true           | true
    ["invalid"]                    | false          | false
    ["test-prop"]                  | false          | true
    ["test-env"]                   | false          | true
    ["disabled-prop"]              | true           | false
    ["disabled-env"]               | true           | false
    ["other", "test-prop"]         | false          | true
    ["other", "test-env"]          | false          | true
    ["order"]                      | false          | true
    ["test-prop", "disabled-prop"] | false          | true
    ["disabled-env", "test-env"]   | false          | true
    ["test-prop", "disabled-prop"] | true           | false
    ["disabled-env", "test-env"]   | true           | false

    integrationNames = new TreeSet<String>(names)
  }
}
