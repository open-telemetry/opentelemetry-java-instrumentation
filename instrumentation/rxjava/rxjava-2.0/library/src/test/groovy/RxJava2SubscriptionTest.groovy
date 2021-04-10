/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.rxjava2.AbstractRxJava2SubscriptionTest
import io.opentelemetry.instrumentation.rxjava2.TracingAssembly
import io.opentelemetry.instrumentation.test.LibraryTestTrait

class RxJava2SubscriptionTest extends AbstractRxJava2SubscriptionTest implements LibraryTestTrait {

  def setupSpec() {
    TracingAssembly.enable()
  }
}
