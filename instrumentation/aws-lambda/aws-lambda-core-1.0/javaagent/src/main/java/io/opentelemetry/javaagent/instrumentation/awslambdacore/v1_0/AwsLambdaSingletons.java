/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdacore.v1_0;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenterFactory;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrapperConfiguration;
import java.time.Duration;

public final class AwsLambdaSingletons {

  private static final AwsLambdaFunctionInstrumenter FUNCTION_INSTRUMENTER;
  private static final Duration FLUSH_TIMEOUT;

  static {
    Configuration config = new Configuration(GlobalOpenTelemetry.get());

    FLUSH_TIMEOUT = Duration.ofMillis(config.flushTimeout);
    FUNCTION_INSTRUMENTER =
        AwsLambdaFunctionInstrumenterFactory.createInstrumenter(GlobalOpenTelemetry.get());
  }

  public static AwsLambdaFunctionInstrumenter functionInstrumenter() {
    return FUNCTION_INSTRUMENTER;
  }

  public static Duration flushTimeout() {
    return FLUSH_TIMEOUT;
  }

  // instrumentation/development:
  //   java:
  //     aws_lambda:
  //       flush_timeout: 10000
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
      DeclarativeConfigProperties awsLambdaConfig = javaConfig.getStructured("aws_lambda", empty());

      this.flushTimeout =
          awsLambdaConfig.getLong(
              "flush_timeout", WrapperConfiguration.OTEL_LAMBDA_FLUSH_TIMEOUT_DEFAULT.toMillis());
    }
  }

  private AwsLambdaSingletons() {}
}
