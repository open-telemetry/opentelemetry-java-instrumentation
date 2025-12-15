/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrapperConfiguration;
import io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal.AwsLambdaEventsInstrumenterFactory;
import io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal.AwsLambdaSqsInstrumenterFactory;
import java.time.Duration;

public final class AwsLambdaSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.aws-lambda-events-2.2";

  private static final AwsLambdaFunctionInstrumenter FUNCTION_INSTRUMENTER;
  private static final Instrumenter<SQSEvent, Void> MESSAGE_TRACER;
  private static final Duration FLUSH_TIMEOUT;

  static {
    Configuration config = new Configuration(GlobalOpenTelemetry.get());

    FUNCTION_INSTRUMENTER =
        AwsLambdaEventsInstrumenterFactory.createInstrumenter(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME);
    MESSAGE_TRACER =
        AwsLambdaSqsInstrumenterFactory.forEvent(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME);
    FLUSH_TIMEOUT = Duration.ofMillis(config.flushTimeout);
  }

  public static AwsLambdaFunctionInstrumenter functionInstrumenter() {
    return FUNCTION_INSTRUMENTER;
  }

  public static Instrumenter<SQSEvent, Void> messageInstrumenter() {
    return MESSAGE_TRACER;
  }

  public static Duration flushTimeout() {
    return FLUSH_TIMEOUT;
  }

  // instrumentation/development:
  //   java:
  //     aws_lambda:
  //       flush_timeout: 10000
  //     http:
  //       known_methods:
  //         - CONNECT
  //         - DELETE
  //         - GET
  //         - HEAD
  //         - OPTIONS
  //         - PATCH
  //         - POST
  //         - PUT
  //         - TRACE
  private static final class Configuration {

    private final long flushTimeout;

    Configuration(OpenTelemetry openTelemetry) {
      DeclarativeConfigProperties javaConfig = empty();
      if (openTelemetry instanceof ExtendedOpenTelemetry) {
        ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
        DeclarativeConfigProperties instrumentationConfig =
            extendedOpenTelemetry.getConfigProvider().getInstrumentationConfig();
        if (instrumentationConfig != null) {
          javaConfig = instrumentationConfig.getStructured("java", empty());
        }
      }

      this.flushTimeout =
          javaConfig
              .getStructured("aws_lambda", empty())
              .getLong(
                  "flush_timeout",
                  WrapperConfiguration.OTEL_LAMBDA_FLUSH_TIMEOUT_DEFAULT.toMillis());
    }
  }

  private AwsLambdaSingletons() {}
}
