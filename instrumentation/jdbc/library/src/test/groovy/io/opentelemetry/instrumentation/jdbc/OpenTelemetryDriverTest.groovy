/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc

import io.opentelemetry.instrumentation.api.InstrumentationVersion
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection
import spock.lang.Specification

import java.sql.DriverManager
import java.sql.SQLFeatureNotSupportedException

class OpenTelemetryDriverTest extends Specification {

  def cleanup() {
    if (!OpenTelemetryDriver.registered) {
      OpenTelemetryDriver.register()
    }
  }

  def "verify driver auto registered"() {
    when:
    Class driverClass = Class.forName("io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver")
    def drivers = DriverManager.drivers

    then:
    driverClass != null
    OpenTelemetryDriver.registered
    drivers.any { driver ->
      driver instanceof OpenTelemetryDriver && driver == OpenTelemetryDriver.INSTANCE
    }
  }

  def "verify standard properties"() {
    expect:
    !OpenTelemetryDriver.INSTANCE.jdbcCompliant()

    String[] parts = InstrumentationVersion.getPackage().getImplementationVersion().split("\\.")

    OpenTelemetryDriver.INSTANCE.majorVersion == Integer.parseInt(parts[0])
    OpenTelemetryDriver.INSTANCE.minorVersion == Integer.parseInt(parts[1])
  }

  def "verify parent logger thrown an exception"() {
    when:
    OpenTelemetryDriver.INSTANCE.parentLogger

    then:
    def e = thrown(SQLFeatureNotSupportedException)
    e.message == "Feature not supported"
  }

  def "verify accepted urls"() {
    expect:
    def driver = OpenTelemetryDriver.INSTANCE
    driver.acceptsURL(url) == expected

    where:
    url                                            | expected
    null                                           | false
    ""                                             | false
    "jdbc:"                                        | false
    "jdbc::"                                       | false
    "bogus:string"                                 | false
    "jdbc:postgresql://127.0.0.1:5432/dbname"      | false
    "jdbc:otel:postgresql://127.0.0.1:5432/dbname" | true
  }

  def "verify deregister"() {
    when:
    if (OpenTelemetryDriver.registered) {
      OpenTelemetryDriver.deregister()
    }

    then:
    !OpenTelemetryDriver.registered
    DriverManager.drivers.every { driver ->
      !(driver instanceof OpenTelemetryDriver)
    }
  }

  def "verify register"() {
    when:
    if (OpenTelemetryDriver.registered) {
      OpenTelemetryDriver.deregister()
    }
    OpenTelemetryDriver.register()

    then:
    OpenTelemetryDriver.registered
    DriverManager.drivers.any { driver ->
      driver instanceof OpenTelemetryDriver && driver == OpenTelemetryDriver.INSTANCE
    }
  }

  def "verify connection with null url"() {
    when:
    OpenTelemetryDriver.INSTANCE.connect(null, null)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "url is required"
  }

  def "verify connection with empty url"() {
    when:
    OpenTelemetryDriver.INSTANCE.connect(" ", null)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "url is required"
  }

  def "verify connection with not accepted url"() {
    when:
    def connection = OpenTelemetryDriver.INSTANCE.connect("abc:xyz", null)

    then:
    connection == null
  }

  def "verify connection with accepted url"() {
    when:
    registerTestDriver()
    def connection = OpenTelemetryDriver.INSTANCE.connect("jdbc:otel:test:", null)

    then:
    connection != null
    connection instanceof OpenTelemetryConnection
  }

  def "verify get property info with null url"() {
    when:
    OpenTelemetryDriver.INSTANCE.getPropertyInfo(null, null)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "url is required"
  }

  def "verify get property info with empty url"() {
    when:
    OpenTelemetryDriver.INSTANCE.getPropertyInfo(" ", null)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "url is required"
  }

  def "verify get property info with unknown driver url"() {
    when:
    def realUrl = "jdbc:unknown"
    OpenTelemetryDriver.INSTANCE.getPropertyInfo(realUrl, null)

    then:
    def e = thrown(IllegalStateException)
    e.message == "Unable to find a driver that accepts url: ${realUrl}"
  }

  def "verify get property info with test driver url"() {
    when:
    registerTestDriver()
    def realUrl = "jdbc:otel:test:"
    def propertyInfos = OpenTelemetryDriver.INSTANCE.getPropertyInfo(realUrl, null)

    then:
    propertyInfos.size() == 1
    propertyInfos[0].name == "test"
    propertyInfos[0].value == "test"
  }

  private static void registerTestDriver() {
    if (!(DriverManager.drivers.any { it instanceof TestDriver })) {
      DriverManager.registerDriver(new TestDriver())
    }
  }

}
