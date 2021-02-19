/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambda.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaMessageTracer;
import io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaTracer;

public final class AwsLambdaInstrumentationHelper {

  private static final AwsLambdaTracer FUNCTION_TRACER =
      new AwsLambdaTracer(GlobalOpenTelemetry.get());

  public static AwsLambdaTracer functionTracer() {
    return FUNCTION_TRACER;
  }

  private static final AwsLambdaMessageTracer MESSAGE_TRACER =
      new AwsLambdaMessageTracer(GlobalOpenTelemetry.get());

  public static AwsLambdaMessageTracer messageTracer() {
    return MESSAGE_TRACER;
  }

  private AwsLambdaInstrumentationHelper() {}
}
