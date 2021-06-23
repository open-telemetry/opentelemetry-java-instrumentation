/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc

import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection
import spock.lang.Specification

import java.sql.DriverManager

class OpenTelemetryDriverTest extends Specification {

  def cleanup() {
    OpenTelemetryDriver.setInterceptorMode(false)
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
    OpenTelemetryDriver.INSTANCE.parentLogger == null
    OpenTelemetryDriver.INSTANCE.majorVersion == 1
    OpenTelemetryDriver.INSTANCE.minorVersion == 4
  }

  def "verify driver registered as a first driver"() {
    given:
    DriverManager.drivers.each { driver ->
      if (driver instanceof OpenTelemetryDriver) {
        OpenTelemetryDriver.deregister()
      } else {
        DriverManager.deregisterDriver(driver)
      }
    }
    DriverManager.registerDriver(new TestDriver())
    DriverManager.registerDriver(new AnotherTestDriver())
    OpenTelemetryDriver.ensureRegisteredAsTheFirstDriver()

    when:
    def firstDriver = DriverManager.drivers.nextElement()

    then:
    OpenTelemetryDriver.registered
    firstDriver != null
    firstDriver instanceof OpenTelemetryDriver
  }

  def "verify accepted urls"() {
    expect:
    def driver = OpenTelemetryDriver.INSTANCE
    OpenTelemetryDriver.setInterceptorMode(interceptorMode)
    driver.acceptsURL(url) == expected

    where:
    url                                            | interceptorMode | expected
    null                                           | false           | false
    ""                                             | false           | false
    "jdbc:"                                        | false           | false
    "jdbc::"                                       | false           | false
    "bogus:string"                                 | false           | false
    "jdbc:postgresql://127.0.0.1:5432/dbname"      | false           | false
    "jdbc:otel:postgresql://127.0.0.1:5432/dbname" | false           | true
    null                                           | true            | false
    ""                                             | true            | false
    "jdbc:"                                        | true            | false
    "jdbc::"                                       | true            | true
    "bogus:string"                                 | true            | false
    "jdbc:postgresql://127.0.0.1:5432/dbname"      | true            | true
    "jdbc:otel:postgresql://127.0.0.1:5432/dbname" | true            | true
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

  def "verify connection with disabled interceptor mode"() {
    when:
    OpenTelemetryDriver.interceptorMode = false
    def connection = OpenTelemetryDriver.INSTANCE.connect("jdbc:test:", null)

    then:
    connection == null

    when:
    registerTestDriver()
    OpenTelemetryDriver.interceptorMode = false
    connection = OpenTelemetryDriver.INSTANCE.connect("jdbc:otel:test:", null)

    then:
    connection != null
    connection instanceof OpenTelemetryConnection
  }

  def "verify connection with enabled interceptor mode"() {
    when:
    registerTestDriver()
    OpenTelemetryDriver.interceptorMode = true
    def connection = OpenTelemetryDriver.INSTANCE.connect("jdbc:test:", null)

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
