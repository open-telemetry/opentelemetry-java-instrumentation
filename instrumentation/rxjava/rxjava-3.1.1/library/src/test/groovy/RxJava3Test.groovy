/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.rxjava3.AbstractRxJava3Test
import io.opentelemetry.instrumentation.rxjava3.v3_1_1.TracingAssembly
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import spock.lang.Shared

class RxJava3Test extends AbstractRxJava3Test implements LibraryTestTrait {
  @Shared
  TracingAssembly tracingAssembly = TracingAssembly.create()

  def setupSpec() {
    tracingAssembly.enable()
  }
}
