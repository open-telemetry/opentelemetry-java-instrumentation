/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config

import spock.lang.Specification

class ConfigTest extends Specification {
  def "verify instrumentation config"() {
    setup:
    def config = new ConfigBuilder().readProperties([
      "otel.instrumentation.order.enabled"        : "true",
      "otel.instrumentation.test-prop.enabled"    : "true",
      "otel.instrumentation.disabled-prop.enabled": "false",
      "otel.instrumentation.test-env.enabled"     : "true",
      "otel.instrumentation.disabled-env.enabled" : "false"
    ]).build()

    expect:
    config.isInstrumentationEnabled(instrumentationNames, defaultEnabled) == expected

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

    instrumentationNames = new TreeSet<String>(names)
  }

  def "should get string property"() {
    given:
    def config = new ConfigBuilder().readProperties([
      "property.string": "whatever"
    ]).build()

    expect:
    config.getProperty("property.string") == "whatever"
    config.getProperty("property.string", "default") == "whatever"
    config.getProperty("does-not-exist") == null
    config.getProperty("does-not-exist", "default") == "default"
  }

  def "should get boolean property"() {
    given:
    def config = new ConfigBuilder().readProperties([
      "property.bool": "false"
    ]).build()

    expect:
    !config.getBooleanProperty("property.bool", true)
    config.getBooleanProperty("does-not-exist", true)
  }

  def "should get list property"() {
    given:
    def config = new ConfigBuilder().readProperties([
      "property.list": "one, two, three"
    ]).build()

    expect:
    config.getListProperty("property.list") == ["one", "two", "three"]
    config.getListProperty("property.list", ["four"]) == ["one", "two", "three"]
    config.getListProperty("does-not-exist") == []
    config.getListProperty("does-not-exist", ["four"]) == ["four"]
  }

  def "should get map property"() {
    given:
    def config = new ConfigBuilder().readProperties([
      "property.map": "one=1, two=2"
    ]).build()

    expect:
    config.getMapProperty("property.map") == ["one": "1", "two": "2"]
    config.getMapProperty("does-not-exist").isEmpty()
  }

  def "should return empty map when map property value is invalid"() {
    given:
    def config = new ConfigBuilder().readProperties([
      "property.map": "one=1, broken!"
    ]).build()

    expect:
    config.getMapProperty("property.map").isEmpty()
  }

  def "should expose if agent debug is enabled"() {
    given:
    def config = new ConfigBuilder().readProperties([
      "otel.javaagent.debug": value
    ]).build()

    expect:
    config.isAgentDebugEnabled() == result

    where:
    value     | result
    "true"    | true
    "blather" | false
    null      | false
  }
}
