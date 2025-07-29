/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdacore.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenterFactory;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrapperConfiguration;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.time.Duration;

public final class AwsLambdaInstrumentationHelper {

  private static final AwsLambdaFunctionInstrumenter FUNCTION_INSTRUMENTER =
      AwsLambdaFunctionInstrumenterFactory.createInstrumenter(GlobalOpenTelemetry.get());
  private static final Duration FLUSH_TIMEOUT =
      Duration.ofMillis(
          AgentInstrumentationConfig.get()
              .getLong(
                  "otel.instrumentation.aws-lambda.flush-timeout",
                  WrapperConfiguration.OTEL_LAMBDA_FLUSH_TIMEOUT_DEFAULT.toMillis()));

  public static AwsLambdaFunctionInstrumenter functionInstrumenter() {
    return FUNCTION_INSTRUMENTER;
  }

  public static Duration flushTimeout() {
    return FLUSH_TIMEOUT;
  }

  private AwsLambdaInstrumentationHelper() {}
}
