/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.faas.internal.FaasExceptionEventExtractors.setFaasInvocationExceptionEventExtractor;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.semconv.SchemaUrls;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsLambdaFunctionInstrumenterFactory {

  public static AwsLambdaFunctionInstrumenter createInstrumenter(OpenTelemetry openTelemetry) {
    InstrumenterBuilder<AwsLambdaRequest, Object> builder =
        Instrumenter.<AwsLambdaRequest, Object>builder(
                openTelemetry,
                "io.opentelemetry.aws-lambda-core-1.0",
                AwsLambdaFunctionInstrumenterFactory::spanName)
            .setSchemaUrl(SchemaUrls.V1_44_0)
            .addAttributesExtractor(new AwsLambdaFunctionAttributesExtractor());
    setFaasInvocationExceptionEventExtractor(builder);

    return new AwsLambdaFunctionInstrumenter(
        openTelemetry, builder.buildInstrumenter(SpanKindExtractor.alwaysServer()));
  }

  private static String spanName(AwsLambdaRequest input) {
    return input.getAwsContext().getFunctionName();
  }

  private AwsLambdaFunctionInstrumenterFactory() {}
}
