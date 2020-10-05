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
}
