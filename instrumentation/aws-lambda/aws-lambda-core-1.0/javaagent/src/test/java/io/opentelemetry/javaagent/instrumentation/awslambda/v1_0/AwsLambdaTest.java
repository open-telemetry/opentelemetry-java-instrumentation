/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambda.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.opentelemetry.instrumentation.awslambda.v1_0.AbstractAwsLambdaTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AwsLambdaTest extends AbstractAwsLambdaTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected RequestHandler<String, String> handler() {
    return new TestRequestHandler();
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @AfterEach
  void tearDown() {
    assertThat(testing.forceFlushCalled()).isTrue();
  }

  private static final class TestRequestHandler implements RequestHandler<String, String> {

    @Override
    public String handleRequest(String input, Context context) {
      return doHandleRequest(input, context);
    }
  }
}
