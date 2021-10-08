/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import spock.lang.Specification

class UtilsTest extends Specification {

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

}
