/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.awslambda.v1_0;

import io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaMessageTracer;
import io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaTracer;

public final class AwsLambdaInstrumentationHelper {

  public static final AwsLambdaTracer FUNCTION_TRACER = new AwsLambdaTracer();
  public static final AwsLambdaMessageTracer MESSAGE_TRACER = new AwsLambdaMessageTracer();

  private AwsLambdaInstrumentationHelper() {}
}
