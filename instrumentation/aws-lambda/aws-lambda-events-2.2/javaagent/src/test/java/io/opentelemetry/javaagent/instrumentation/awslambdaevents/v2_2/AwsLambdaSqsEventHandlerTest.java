/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.AbstractAwsLambdaSqsEventHandlerTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AwsLambdaSqsEventHandlerTest extends AbstractAwsLambdaSqsEventHandlerTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected RequestHandler<SQSEvent, Void> handler() {
    return new TestRequestHandler();
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  private static final class TestRequestHandler implements RequestHandler<SQSEvent, Void> {
    @Override
    public Void handleRequest(SQSEvent input, Context context) {
      return null;
    }
  }
}
