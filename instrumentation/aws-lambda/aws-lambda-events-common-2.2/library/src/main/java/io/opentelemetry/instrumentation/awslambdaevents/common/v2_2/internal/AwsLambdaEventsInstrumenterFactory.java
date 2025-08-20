/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionAttributesExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsLambdaEventsInstrumenterFactory {

  public static AwsLambdaFunctionInstrumenter createInstrumenter(
      OpenTelemetry openTelemetry, String instrumentationName, Set<String> knownMethods) {
    return new AwsLambdaFunctionInstrumenter(
        openTelemetry,
        Instrumenter.builder(
                openTelemetry, instrumentationName, AwsLambdaEventsInstrumenterFactory::spanName)
            .addAttributesExtractor(new AwsLambdaFunctionAttributesExtractor())
            .addAttributesExtractor(new ApiGatewayProxyAttributesExtractor(knownMethods))
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

  private AwsLambdaEventsInstrumenterFactory() {}
}
