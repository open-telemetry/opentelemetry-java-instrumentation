/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import spock.lang.Shared

class ReactorCoreTest extends AbstractReactorCoreTest implements LibraryTestTrait {
  @Shared
  TracingOperator tracingOperator = TracingOperator.create()

  def setupSpec() {
    tracingOperator.registerOnEachOperator()
  }

  def cleanupSpec() {
    tracingOperator.resetOnEachOperator()
  }
}
