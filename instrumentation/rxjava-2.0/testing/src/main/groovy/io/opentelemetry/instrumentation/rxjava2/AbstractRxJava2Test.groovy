/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2

import io.opentelemetry.instrumentation.test.LibraryInstrumentationSpecification
import spock.lang.Shared

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

abstract class AbstractRxJava2Test extends LibraryInstrumentationSpecification {

  public static final String EXCEPTION_MESSAGE = "test exception"

  @Shared
  def addOne = { i ->
    addOneFunc(i)
  }

  @Shared
  def addTwo = { i ->
    addTwoFunc(i)
  }

  @Shared
  def throwException = {
    throw new RuntimeException(EXCEPTION_MESSAGE)
  }

  static addOneFunc(int i) {
    runUnderTrace("addOne") {
      return i + 1
    }
  }

  static addTwoFunc(int i) {
    runUnderTrace("addTwo") {
      return i + 2
    }
  }
}
