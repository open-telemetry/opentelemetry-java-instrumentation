/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.rxjava.v2_0.AbstractRxJava2Test
import io.opentelemetry.instrumentation.rxjava.v2_0.TracingAssembly
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import spock.lang.Shared

class RxJava2Test extends AbstractRxJava2Test implements LibraryTestTrait {
  @Shared
  TracingAssembly tracingAssembly = TracingAssembly.create()

  def setupSpec() {
    tracingAssembly.enable()
  }
}
