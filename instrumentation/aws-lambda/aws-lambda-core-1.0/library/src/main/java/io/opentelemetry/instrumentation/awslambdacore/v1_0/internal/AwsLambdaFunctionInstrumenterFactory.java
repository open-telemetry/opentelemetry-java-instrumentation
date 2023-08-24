/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsLambdaFunctionInstrumenterFactory {
  private static final Boolean SHOULD_LINK_XRAY_SPANS =
      ConfigPropertiesUtil.getBoolean("otel.instrumentation.aws-lambda.link-xray-traces", false);

  public static AwsLambdaFunctionInstrumenter createInstrumenter(OpenTelemetry openTelemetry) {
    return createInstrumenter(openTelemetry, SHOULD_LINK_XRAY_SPANS);
  }

  public static AwsLambdaFunctionInstrumenter createInstrumenter(
      OpenTelemetry openTelemetry, boolean linkXraySpans) {
    InstrumenterBuilder<AwsLambdaRequest, Object> otelInstrumenterBuilder =
        Instrumenter.builder(
                openTelemetry,
                "io.opentelemetry.aws-lambda-core-1.0",
                AwsLambdaFunctionInstrumenterFactory::spanName)
            .addAttributesExtractor(new AwsLambdaFunctionAttributesExtractor());

    if (linkXraySpans) {
      otelInstrumenterBuilder.addSpanLinksExtractor(new AwsXrayEnvSpanLinksExtractor());
    }

    return new AwsLambdaFunctionInstrumenter(
        openTelemetry,
        otelInstrumenterBuilder.buildInstrumenter(SpanKindExtractor.alwaysServer()),
        new AwsXrayEnvCarrierEnricher());
  }

  private static String spanName(AwsLambdaRequest input) {
    return input.getAwsContext().getFunctionName();
  }

  private AwsLambdaFunctionInstrumenterFactory() {}
}
