/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.rxjava3.AbstractRxJava3Test
import io.opentelemetry.instrumentation.rxjava3.TracingAssembly
import io.opentelemetry.instrumentation.test.LibraryTestTrait

class RxJava3Test extends AbstractRxJava3Test implements LibraryTestTrait {

  def setupSpec() {
    TracingAssembly.enable()
  }
}
