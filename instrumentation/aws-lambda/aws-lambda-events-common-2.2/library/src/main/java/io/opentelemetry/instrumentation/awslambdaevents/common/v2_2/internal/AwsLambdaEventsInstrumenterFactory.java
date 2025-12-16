/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionAttributesExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsLambdaEventsInstrumenterFactory {

  public static AwsLambdaFunctionInstrumenter createInstrumenter(
      OpenTelemetry openTelemetry, String instrumentationName) {
    Configuration config = new Configuration(openTelemetry);
    return new AwsLambdaFunctionInstrumenter(
        openTelemetry,
        Instrumenter.builder(
                openTelemetry, instrumentationName, AwsLambdaEventsInstrumenterFactory::spanName)
            .addAttributesExtractor(new AwsLambdaFunctionAttributesExtractor())
            .addAttributesExtractor(new ApiGatewayProxyAttributesExtractor(config.knownHttpMethods))
            .buildInstrumenter(SpanKindExtractor.alwaysServer()));
  }

  private static String spanName(AwsLambdaRequest input) {
    if (input.getInput() instanceof APIGatewayProxyRequestEvent) {
      APIGatewayProxyRequestEvent request = (APIGatewayProxyRequestEvent) input.getInput();
      String method = request.getHttpMethod();
      String route = request.getResource();
      if (method != null && route != null) {
        return method + " " + route;
      }
    }
    return input.getAwsContext().getFunctionName();
  }

  // instrumentation/development:
  //   java:
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

    private final Set<String> knownHttpMethods;

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
      DeclarativeConfigProperties httpConfig = javaConfig.getStructured("http", empty());

      this.knownHttpMethods =
          new HashSet<>(
              httpConfig.getScalarList(
                  "known_methods", String.class, new ArrayList<>(HttpConstants.KNOWN_METHODS)));
    }
  }

  private AwsLambdaEventsInstrumenterFactory() {}
}
