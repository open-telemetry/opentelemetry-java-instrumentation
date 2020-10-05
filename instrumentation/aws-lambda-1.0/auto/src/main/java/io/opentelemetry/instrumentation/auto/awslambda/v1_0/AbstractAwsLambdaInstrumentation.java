/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.awslambda.v1_0;

import io.opentelemetry.javaagent.tooling.Instrumenter;

public abstract class AbstractAwsLambdaInstrumentation extends Instrumenter.Default {

  public AbstractAwsLambdaInstrumentation() {
    super("aws-lambda");
  }

  @Override
  public final String[] helperClassNames() {
    return new String[] {
      packageName + ".AwsLambdaInstrumentationHelper",
      "io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaTracer",
      "io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaMessageTracer",
      "io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaMessageTracer$MapGetter"
    };
  }
}
