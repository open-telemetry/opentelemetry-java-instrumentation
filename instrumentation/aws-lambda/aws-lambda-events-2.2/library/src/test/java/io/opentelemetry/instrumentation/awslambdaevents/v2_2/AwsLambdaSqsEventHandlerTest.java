/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AwsLambdaSqsEventHandlerTest extends AbstractAwsLambdaSqsEventHandlerTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected RequestHandler<SQSEvent, Void> handler() {
    return new TestHandler(testing.getOpenTelemetrySdk());
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  private static final class TestHandler extends TracingSqsEventHandler {

    TestHandler(OpenTelemetrySdk openTelemetrySdk) {
      super(openTelemetrySdk);
    }

    @Override
    protected void handleEvent(SQSEvent event, Context context) {}
  }
}
