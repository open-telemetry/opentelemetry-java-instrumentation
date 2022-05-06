/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.rxjava.v3.common.AbstractRxJava3SubscriptionTest
import io.opentelemetry.instrumentation.rxjava.v3_0.TracingAssembly
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import spock.lang.Shared

class RxJava3SubscriptionTest extends AbstractRxJava3SubscriptionTest implements LibraryTestTrait {
  @Shared
  TracingAssembly tracingAssembly = TracingAssembly.create()

  def setupSpec() {
    tracingAssembly.enable()
  }
}
