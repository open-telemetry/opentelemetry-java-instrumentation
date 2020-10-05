/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.opentelemetry.auto.test.InstrumentationTestTrait

class AwsLambdaSqsHandlerTest extends AbstractAwsLambdaSqsHandlerTest implements InstrumentationTestTrait {

  class TestHandler extends TracingSQSEventHandler {
    @Override
    protected void handleEvent(SQSEvent event, Context context) {
    }
  }

  @Override
  RequestHandler<SQSEvent, Void> handler() {
    return new TestHandler()
  }
}
