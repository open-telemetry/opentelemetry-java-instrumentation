/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrapperConfiguration;
import io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal.AwsLambdaEventsInstrumenterFactory;
import io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal.AwsLambdaSqsInstrumenterFactory;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.time.Duration;

public final class AwsLambdaSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.aws-lambda-events-2.2";
  private static final AwsLambdaFunctionInstrumenter FUNCTION_INSTRUMENTER =
      AwsLambdaEventsInstrumenterFactory.createInstrumenter(
          GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME);
  private static final Instrumenter<SQSEvent, Void> MESSAGE_TRACER =
      AwsLambdaSqsInstrumenterFactory.forEvent(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME);
  private static final Duration FLUSH_TIMEOUT =
      Duration.ofMillis(
          AgentInstrumentationConfig.get()
              .getLong(
                  "otel.instrumentation.aws-lambda.flush-timeout",
                  WrapperConfiguration.OTEL_LAMBDA_FLUSH_TIMEOUT_DEFAULT.toMillis()));

  public static AwsLambdaFunctionInstrumenter functionInstrumenter() {
    return FUNCTION_INSTRUMENTER;
  }

  public static Instrumenter<SQSEvent, Void> messageInstrumenter() {
    return MESSAGE_TRACER;
  }

  public static Duration flushTimeout() {
    return FLUSH_TIMEOUT;
  }

  private AwsLambdaSingletons() {}
}
