/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import spock.lang.Shared

class SubscriptionTest extends AbstractSubscriptionTest implements LibraryTestTrait {
  @Shared
  ContextPropagationOperator tracingOperator = ContextPropagationOperator.create()

  def setupSpec() {
    tracingOperator.registerOnEachOperator()
  }

  def cleanupSpec() {
    tracingOperator.resetOnEachOperator()
  }
}
