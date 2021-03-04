/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.sdk.OpenTelemetrySdk

class AwsLambdaSqsHandlerTest extends AbstractAwsLambdaSqsHandlerTest implements LibraryTestTrait {

  static class TestHandler extends TracingSqsEventHandler {

    TestHandler(OpenTelemetrySdk openTelemetrySdk) {
      super(openTelemetrySdk)
    }

    @Override
    protected void handleEvent(SQSEvent event, Context context) {
    }
  }

  @Override
  RequestHandler<SQSEvent, Void> handler() {
    return new TestHandler(testRunner().openTelemetrySdk)
  }
}
