/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaIntrumenterFactory;
import io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaTracer;

public final class AwsLambdaInstrumentationHelper {

  private static final AwsLambdaTracer FUNCTION_TRACER =
      new AwsLambdaTracer(GlobalOpenTelemetry.get());

  public static AwsLambdaTracer functionTracer() {
    return FUNCTION_TRACER;
  }

  private static final Instrumenter<SQSEvent, Void> MESSAGE_TRACER =
      AwsLambdaIntrumenterFactory.forEvent(GlobalOpenTelemetry.get());

  public static Instrumenter<SQSEvent, Void> messageInstrumenter() {
    return MESSAGE_TRACER;
  }

  private AwsLambdaInstrumentationHelper() {}
}
