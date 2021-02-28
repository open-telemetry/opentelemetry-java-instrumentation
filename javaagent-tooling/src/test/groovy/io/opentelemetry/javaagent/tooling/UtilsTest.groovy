/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import spock.lang.Specification

class UtilsTest extends Specification {

  def "getStackTraceAsString() returns the stack trace as a single new line separated string"() {
    setup:
    def stackTrace = Utils.stackTraceAsString

    expect:
    stackTrace.contains('io.opentelemetry.javaagent.tooling.Utils')
    stackTrace.contains(System.getProperty("line.separator"))
  }

  def "getResourceName() adds suffix and converts dots to slashes"() {
    setup:
    def result = Utils.getResourceName("com.example.Something")
    expect:
    result == "com/example/Something.class"
  }

  def "getClassName() converts slashes to dots"() {
    setup:
    def result = Utils.getClassName("com/example/Something")
    expect:
    result == "com.example.Something"
  }

  def "getInternalName() converts slashes to dots"() {
    setup:
    def result = Utils.getInternalName(UtilsTest)
    expect:
    result == "io/opentelemetry/javaagent/tooling/UtilsTest"
  }

  def "convertToInnerClassName() makes dollar into dots"() {
    setup:
    def result = Utils.convertToInnerClassName("com/example/MyOuter.MyInner")
    expect:
    result == 'com/example/MyOuter$MyInner'
  }

}
