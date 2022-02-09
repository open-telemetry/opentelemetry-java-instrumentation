/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.awslambda.v1_0.internal.AwsLambdaEventsInstrumenterFactory;
import io.opentelemetry.instrumentation.awslambda.v1_0.internal.AwsLambdaSqsInstrumenterFactory;

public final class AwsLambdaInstrumentationHelper {

  private static final io.opentelemetry.instrumentation.awslambdacore.v1_0.internal
          .AwsLambdaFunctionInstrumenter
      FUNCTION_INSTRUMENTER =
          AwsLambdaEventsInstrumenterFactory.createInstrumenter(GlobalOpenTelemetry.get());

  public static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal
          .AwsLambdaFunctionInstrumenter
      functionInstrumenter() {
    return FUNCTION_INSTRUMENTER;
  }

  private static final Instrumenter<SQSEvent, Void> MESSAGE_TRACER =
      AwsLambdaSqsInstrumenterFactory.forEvent(GlobalOpenTelemetry.get());

  public static Instrumenter<SQSEvent, Void> messageInstrumenter() {
    return MESSAGE_TRACER;
  }

  private AwsLambdaInstrumentationHelper() {}
}
