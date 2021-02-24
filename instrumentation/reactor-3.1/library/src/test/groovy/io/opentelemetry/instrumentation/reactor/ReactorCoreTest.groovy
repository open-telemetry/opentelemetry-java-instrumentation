/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor

import io.opentelemetry.instrumentation.test.LibraryTestTrait

class ReactorCoreTest extends AbstractReactorCoreTest implements LibraryTestTrait {
  def setupSpec() {
    TracingOperator.registerOnEachOperator()
  }

  def cleanupSpec() {
    TracingOperator.resetOnEachOperator()
  }
}
