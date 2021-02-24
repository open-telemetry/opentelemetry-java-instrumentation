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

  def "getClassName() removes suffix and slashes to dots"(){
    setup:
    def result = Utils.getClassName("com/example/Something.class")
    expect:
    result == "com.example.Something"
  }

  def "getInternalName() removes suffix and slashes to dots"(){
    setup:
    def result = Utils.getInternalName("com.example.Something.class")
    expect:
    result == "com/example/Something"
  }

  def "convertToInnerClassName() makes dollar into dots"(){
    setup:
    def result = Utils.convertToInnerClassName("com/example/MyOuter.MyInner")
    expect:
    result == 'com/example/MyOuter$MyInner'
  }

}
