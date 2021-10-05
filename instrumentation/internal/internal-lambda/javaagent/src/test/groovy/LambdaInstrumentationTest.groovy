/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker

class LambdaInstrumentationTest extends AgentInstrumentationSpecification {

  def "test transform Runnable lambda"() {
    setup:
    Runnable runnable = TestLambda.makeRunnable()

    expect:
    // RunnableInstrumentation adds a VirtualField to all implementors of Runnable. If lambda class
    // is transformed then it must have context store marker interface.
    runnable instanceof VirtualFieldInstalledMarker
    !VirtualFieldInstalledMarker.isAssignableFrom(Runnable)
  }
}
