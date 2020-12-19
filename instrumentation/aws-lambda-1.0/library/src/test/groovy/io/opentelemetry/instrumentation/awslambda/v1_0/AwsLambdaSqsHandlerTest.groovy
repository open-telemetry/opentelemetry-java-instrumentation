/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.extension.trace.propagation.AwsXRayPropagator
import io.opentelemetry.instrumentation.test.InstrumentationTestTrait

class AwsLambdaSqsHandlerTest extends AbstractAwsLambdaSqsHandlerTest implements InstrumentationTestTrait {

  // Lambda instrumentation requires XRay propagator to be enabled.
  static {
    def propagators = ContextPropagators.create(
      TextMapPropagator.composite(W3CTraceContextPropagator.instance, AwsXRayPropagator.instance))
    OpenTelemetry.setGlobalPropagators(propagators)
  }

  static class TestHandler extends TracingSqsEventHandler {
    @Override
    protected void handleEvent(SQSEvent event, Context context) {
    }
  }

  @Override
  RequestHandler<SQSEvent, Void> handler() {
    return new TestHandler()
  }
}
