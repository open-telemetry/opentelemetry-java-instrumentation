/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AwsLambdaTest extends AbstractAwsLambdaTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected RequestHandler<String, String> handler() {
    return new TestRequestHandler(testing.getOpenTelemetrySdk());
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  private static final class TestRequestHandler extends TracingRequestHandler<String, String> {

    TestRequestHandler(OpenTelemetrySdk openTelemetrySdk) {
      super(openTelemetrySdk);
    }

    @Override
    protected String doHandleRequest(String input, Context context) {
      return AbstractAwsLambdaTest.doHandleRequest(input, context);
    }
  }
}
