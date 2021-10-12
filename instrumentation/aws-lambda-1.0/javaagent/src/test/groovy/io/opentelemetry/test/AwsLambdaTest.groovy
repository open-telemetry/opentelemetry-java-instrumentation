/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import io.opentelemetry.instrumentation.awslambda.v1_0.AbstractAwsLambdaRequestHandlerTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.javaagent.testing.common.AgentTestingExporterAccess

class AwsLambdaTest extends AbstractAwsLambdaRequestHandlerTest implements AgentTestTrait {

  def cleanup() {
    assert AgentTestingExporterAccess.forceFlushCalled()
  }

  static class TestRequestHandler implements RequestHandler<String, String> {
    @Override
    String handleRequest(String input, Context context) {
      return doHandleRequest(input, context)
    }
  }

  @Override
  RequestHandler<String, String> handler() {
    return new TestRequestHandler()
  }
}
