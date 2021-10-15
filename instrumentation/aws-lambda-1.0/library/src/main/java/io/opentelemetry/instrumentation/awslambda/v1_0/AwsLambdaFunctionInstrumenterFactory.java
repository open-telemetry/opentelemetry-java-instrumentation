/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public class AwsLambdaFunctionInstrumenterFactory {

  public static AwsLambdaFunctionInstrumenter createInstrumenter(OpenTelemetry openTelemetry) {
    return new AwsLambdaFunctionInstrumenter(
        openTelemetry,
        Instrumenter.newBuilder(
                openTelemetry,
                "io.opentelemetry.aws-lambda-1.0",
                AwsLambdaFunctionInstrumenterFactory::spanName)
            .addAttributesExtractors(
                new AwsLambdaFunctionAttributesExtractor(),
                new ApiGatewayProxyAttributesExtractor())
            .newInstrumenter(SpanKindExtractor.alwaysServer()));
  }

  private static String spanName(AwsLambdaRequest input) {
    String name = null;
    if (input.getInput() instanceof APIGatewayProxyRequestEvent) {
      name = ((APIGatewayProxyRequestEvent) input.getInput()).getResource();
    }
    return name == null ? input.getAwsContext().getFunctionName() : name;
  }
}
