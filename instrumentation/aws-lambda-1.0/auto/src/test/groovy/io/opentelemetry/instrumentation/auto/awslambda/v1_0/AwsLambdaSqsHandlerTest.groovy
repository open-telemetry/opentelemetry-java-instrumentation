/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.awslambda.v1_0

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.opentelemetry.auto.test.AgentTestTrait
import io.opentelemetry.instrumentation.awslambda.v1_0.AbstractAwsLambdaSqsHandlerTest

class AwsLambdaSqsHandlerTest extends AbstractAwsLambdaSqsHandlerTest implements AgentTestTrait {

  class TestRequestHandler implements RequestHandler<SQSEvent, Void> {
    @Override
    Void handleRequest(SQSEvent input, Context context) {
      return null
    }
  }

  @Override
  RequestHandler<SQSEvent, Void> handler() {
    return new TestRequestHandler()
  }
}
