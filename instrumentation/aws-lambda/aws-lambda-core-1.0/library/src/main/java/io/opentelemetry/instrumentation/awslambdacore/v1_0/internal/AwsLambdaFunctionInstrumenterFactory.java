/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import static io.opentelemetry.api.logs.Severity.ERROR;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.ExceptionEventExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.Experimental;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsLambdaFunctionInstrumenterFactory {

  public static AwsLambdaFunctionInstrumenter createInstrumenter(OpenTelemetry openTelemetry) {
    InstrumenterBuilder<AwsLambdaRequest, Object> builder =
        Instrumenter.builder(
                openTelemetry,
                "io.opentelemetry.aws-lambda-core-1.0",
                AwsLambdaFunctionInstrumenterFactory::spanName)
            .addAttributesExtractor(new AwsLambdaFunctionAttributesExtractor());
    Experimental.setExceptionEventExtractor(
        builder, ExceptionEventExtractor.create("faas.invocation.exception", ERROR));
    return new AwsLambdaFunctionInstrumenter(
        openTelemetry, builder.buildInstrumenter(SpanKindExtractor.alwaysServer()));
  }

  private static String spanName(AwsLambdaRequest input) {
    return input.getAwsContext().getFunctionName();
  }

  private AwsLambdaFunctionInstrumenterFactory() {}
}
