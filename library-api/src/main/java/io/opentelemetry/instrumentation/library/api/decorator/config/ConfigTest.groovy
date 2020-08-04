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

package io.opentelemetry.instrumentation.library.api.decorator.config

import io.opentelemetry.auto.util.test.AgentSpecification
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties

import static io.opentelemetry.instrumentation.api.config.Config.CONFIGURATION_FILE
import static io.opentelemetry.instrumentation.api.config.Config.ENDPOINT_PEER_SERVICE_MAPPING
import static io.opentelemetry.instrumentation.api.config.Config.HTTP_CLIENT_ERROR_STATUSES
import static io.opentelemetry.instrumentation.api.config.Config.HTTP_SERVER_ERROR_STATUSES
import static io.opentelemetry.instrumentation.api.config.Config.PREFIX
import static io.opentelemetry.instrumentation.api.config.Config.RUNTIME_CONTEXT_FIELD_INJECTION
import static io.opentelemetry.instrumentation.api.config.Config.TRACE_ENABLED
import static io.opentelemetry.instrumentation.api.config.Config.TRACE_METHODS

class ConfigTest extends AgentSpecification {
  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  private static final TRACE_ENABLED_ENV = "OTEL_TRACE_ENABLED"
  private static final TRACE_METHODS_ENV = "OTEL_TRACE_METHODS"
  private static final ENDPOINT_PEER_NAME_MAPPING_ENV = "OTEL_ENDPOINT_PEER_SERVICE_MAPPING"

  def "verify defaults"() {
    when:
    Config config = provider()

    then:
    config.traceEnabled == true
    config.runtimeContextFieldInjection == true
    config.endpointPeerServiceMapping.isEmpty()
    config.toString().contains("traceEnabled=true")

    where:
    provider << [{ new Config() }, { Config.get() }, {
      def props = new Properties()
      props.setProperty("something", "unused")
      Config.get(props)
    }]
  }

  def "specify overrides via properties"() {
    setup:
    def prop = new Properties()
    prop.setProperty(TRACE_ENABLED, "false")
    prop.setProperty(TRACE_METHODS, "mypackage.MyClass[myMethod]")
    prop.setProperty(HTTP_SERVER_ERROR_STATUSES, "123-456,457,124-125,122")
    prop.setProperty(HTTP_CLIENT_ERROR_STATUSES, "111")
    prop.setProperty(RUNTIME_CONTEXT_FIELD_INJECTION, "false")
    prop.setProperty(ENDPOINT_PEER_SERVICE_MAPPING, "1.2.3.4=cats,dogs.com=dogs")

    when:
    Config config = Config.get(prop)

    then:
    config.traceEnabled == false
    config.traceMethods == "mypackage.MyClass[myMethod]"
    config.runtimeContextFieldInjection == false
    config.endpointPeerServiceMapping.equals(["1.2.3.4": "cats", "dogs.com": "dogs"])
  }

  def "specify overrides via system properties"() {
    setup:
    System.setProperty(PREFIX + TRACE_ENABLED, "false")
    System.setProperty(PREFIX + TRACE_METHODS, "mypackage.MyClass[myMethod]")
    System.setProperty(PREFIX + HTTP_SERVER_ERROR_STATUSES, "123-456,457,124-125,122")
    System.setProperty(PREFIX + HTTP_CLIENT_ERROR_STATUSES, "111")
    System.setProperty(PREFIX + RUNTIME_CONTEXT_FIELD_INJECTION, "false")
    System.setProperty(PREFIX + ENDPOINT_PEER_SERVICE_MAPPING, "1.2.3.4=cats,dogs.com=dogs")

    when:
    Config config = new Config()

    then:
    config.traceEnabled == false
    config.traceMethods == "mypackage.MyClass[myMethod]"
    config.runtimeContextFieldInjection == false
    config.endpointPeerServiceMapping.equals(["1.2.3.4": "cats", "dogs.com": "dogs"])
  }

  def "specify overrides via env vars"() {
    setup:
    environmentVariables.set(TRACE_ENABLED_ENV, "false")
    environmentVariables.set(TRACE_METHODS_ENV, "mypackage.MyClass[myMethod]")
    environmentVariables.set(ENDPOINT_PEER_NAME_MAPPING_ENV, "1.2.3.4=cats,dogs.com=dogs")

    when:
    def config = new Config()

    then:
    config.traceEnabled == false
    config.traceMethods == "mypackage.MyClass[myMethod]"
    config.endpointPeerServiceMapping.equals(["1.2.3.4": "cats", "dogs.com": "dogs"])
  }

  def "sys props override env vars"() {
    setup:
    environmentVariables.set(TRACE_METHODS_ENV, "mypackage.MyClass[myMethod]")

    System.setProperty(PREFIX + TRACE_METHODS, "mypackage2.MyClass2[myMethod2]")

    when:
    def config = new Config()

    then:
    config.traceMethods == "mypackage2.MyClass2[myMethod2]"
  }

  def "default when configured incorrectly"() {
    setup:
    System.setProperty(PREFIX + TRACE_ENABLED, " ")
    System.setProperty(PREFIX + TRACE_METHODS, " ")
    System.setProperty(PREFIX + HTTP_SERVER_ERROR_STATUSES, "1111")
    System.setProperty(PREFIX + HTTP_CLIENT_ERROR_STATUSES, "1:1")
    System.setProperty(PREFIX + ENDPOINT_PEER_SERVICE_MAPPING, "1.2.3.4,dogs=cats")

    when:
    def config = new Config()

    then:
    config.traceEnabled == true
    config.traceMethods == " "
    config.endpointPeerServiceMapping.isEmpty()
  }

  def "sys props override properties"() {
    setup:
    Properties properties = new Properties()
    properties.setProperty(TRACE_ENABLED, "false")
    properties.setProperty(TRACE_METHODS, "mypackage.MyClass[myMethod]")
    properties.setProperty(HTTP_SERVER_ERROR_STATUSES, "123-456,457,124-125,122")
    properties.setProperty(HTTP_CLIENT_ERROR_STATUSES, "111")

    when:
    def config = Config.get(properties)

    then:
    config.traceEnabled == false
    config.traceMethods == "mypackage.MyClass[myMethod]"
  }

  def "override null properties"() {
    when:
    def config = Config.get(null)

    then:
    config.traceEnabled == true
  }

  def "override empty properties"() {
    setup:
    Properties properties = new Properties()

    when:
    def config = Config.get(properties)

    then:
    config.traceEnabled == true
  }

  def "override non empty properties"() {
    setup:
    Properties properties = new Properties()
    properties.setProperty("foo", "bar")

    when:
    def config = Config.get(properties)

    then:
    config.traceEnabled == true
  }

  def "verify integration config"() {
    setup:
    environmentVariables.set("OTEL_INTEGRATION_ORDER_ENABLED", "false")
    environmentVariables.set("OTEL_INTEGRATION_TEST_ENV_ENABLED", "true")
    environmentVariables.set("OTEL_INTEGRATION_DISABLED_ENV_ENABLED", "false")

    System.setProperty("otel.integration.order.enabled", "true")
    System.setProperty("otel.integration.test-prop.enabled", "true")
    System.setProperty("otel.integration.disabled-prop.enabled", "false")

    expect:
    Config.get().isIntegrationEnabled(integrationNames, defaultEnabled) == expected

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

    integrationNames = new TreeSet<>(names)
  }

  def "verify fallback to properties file"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/java-tracer.properties")

    when:
    def config = new Config()

    then:
    config.traceMethods == "mypackage.MyClass[myMethod]"

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
  }

  def "verify fallback to properties file has lower priority than system property"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/java-tracer.properties")
    System.setProperty(PREFIX + TRACE_METHODS, "mypackage2.MyClass2[myMethod2]")

    when:
    def config = new Config()

    then:
    config.traceMethods == "mypackage2.MyClass2[myMethod2]"

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
    System.clearProperty(PREFIX + TRACE_METHODS)
  }

  def "verify fallback to properties file has lower priority than env var"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/java-tracer.properties")
    environmentVariables.set("OTEL_TRACE_METHODS", "mypackage2.MyClass2[myMethod2]")

    when:
    def config = new Config()

    then:
    config.traceMethods == "mypackage2.MyClass2[myMethod2]"

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
    System.clearProperty(PREFIX + TRACE_METHODS)
    environmentVariables.clear("OTEL_TRACE_METHODS")
  }

  def "verify fallback to properties file that does not exist does not crash app"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/do-not-exist.properties")

    when:
    def config = new Config()

    then:
    config.traceEnabled == true

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
  }

  static BitSet toBitSet(Collection<Integer> set) {
    BitSet bs = new BitSet()
    for (Integer i : set) {
      bs.set(i)
    }
    return bs
  }
}
