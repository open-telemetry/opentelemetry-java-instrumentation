/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionAttributesExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsXrayEnvCarrierEnricher;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsXrayEnvSpanLinksExtractor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsLambdaEventsInstrumenterFactory {

  private static final Boolean SHOULD_LINK_XRAY_SPANS =
      ConfigPropertiesUtil.getBoolean("otel.instrumentation.aws-lambda.link-xray-traces", false);

  public static AwsLambdaFunctionInstrumenter createInstrumenter(OpenTelemetry openTelemetry) {
    InstrumenterBuilder<AwsLambdaRequest, Object> otelInstrumenterBuilder =
        Instrumenter.builder(
                openTelemetry,
                "io.opentelemetry.aws-lambda-events-2.2",
                AwsLambdaEventsInstrumenterFactory::spanName)
            .addAttributesExtractor(new AwsLambdaFunctionAttributesExtractor())
            .addAttributesExtractor(new ApiGatewayProxyAttributesExtractor());

    if (SHOULD_LINK_XRAY_SPANS) {
      otelInstrumenterBuilder.addSpanLinksExtractor(new AwsXrayEnvSpanLinksExtractor());
    }

    return new AwsLambdaFunctionInstrumenter(
        openTelemetry,
        otelInstrumenterBuilder.buildInstrumenter(SpanKindExtractor.alwaysServer()),
        new AwsXrayEnvCarrierEnricher());
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
