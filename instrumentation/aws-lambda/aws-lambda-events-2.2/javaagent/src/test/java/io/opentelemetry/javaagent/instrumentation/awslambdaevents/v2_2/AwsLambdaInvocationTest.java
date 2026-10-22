/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AbstractAwsLambdaTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class AwsLambdaInvocationTest extends AbstractAwsLambdaTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected RequestHandler<String, String> handler() {
    return new TestHandler();
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  private static class TestHandler implements RequestHandler<String, String> {
    @Override
    public String handleRequest(String input, Context context) {
      return doHandleRequest(input, context);
    }
  }
}
