/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.opentelemetry.api.trace.propagation.HttpTraceContext
import io.opentelemetry.context.propagation.DefaultContextPropagators
import io.opentelemetry.extension.trace.propagation.AwsXRayPropagator
import io.opentelemetry.instrumentation.test.InstrumentationTestRunner
import io.opentelemetry.instrumentation.test.InstrumentationTestTrait

class AwsLambdaSqsHandlerTest extends AbstractAwsLambdaSqsHandlerTest implements InstrumentationTestTrait {

  // Lambda instrumentation requires XRay propagator to be enabled.
  static {
    def propagators = DefaultContextPropagators.builder()
      .addTextMapPropagator(HttpTraceContext.instance)
      .addTextMapPropagator(AwsXRayPropagator.instance)
      .build()
    InstrumentationTestRunner.setGlobalPropagators(propagators)
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
