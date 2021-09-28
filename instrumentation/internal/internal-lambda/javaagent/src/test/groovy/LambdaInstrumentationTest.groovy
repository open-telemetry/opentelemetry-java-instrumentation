/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.bootstrap.FieldBackedContextStoreAppliedMarker

class LambdaInstrumentationTest extends AgentInstrumentationSpecification {

  def "test transform Runnable lambda"() {
    setup:
    Runnable runnable = TestLambda.makeRunnable()

    expect:
    // RunnableInstrumentation adds a ContextStore to all implementors of Runnable. If lambda class
    // is transformed then it must have context store marker interface.
    runnable instanceof FieldBackedContextStoreAppliedMarker
    !FieldBackedContextStoreAppliedMarker.isAssignableFrom(Runnable)
  }
}
