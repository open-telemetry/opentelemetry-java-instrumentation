/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.graphql.internal.OpenTelemetryInstrumentationHelper;

final class GraphqlInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.graphql-java-20.0";

  static OpenTelemetryInstrumentationHelper createInstrumentationHelper(
      OpenTelemetry openTelemetry,
      boolean captureQuery,
      boolean sanitizeQuery,
      boolean addOperationNameToSpanName) {
    return OpenTelemetryInstrumentationHelper.create(
        openTelemetry,
        INSTRUMENTATION_NAME,
        captureQuery,
        sanitizeQuery,
        addOperationNameToSpanName);
  }

  static Instrumenter<DataFetchingEnvironment, Object> createDataFetcherInstrumenter(
      OpenTelemetry openTelemetry, boolean enabled) {
    return Instrumenter.<DataFetchingEnvironment, Object>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            environment -> environment.getExecutionStepInfo().getField().getName())
        .addAttributesExtractor(new GraphqlDataFetcherAttributesExtractor())
        .setSpanStatusExtractor(
            (spanStatusBuilder, dataFetchingEnvironment, result, error) -> {
              if (result instanceof DataFetcherResult
                  && ((DataFetcherResult<?>) result).hasErrors()) {
                spanStatusBuilder.setStatus(StatusCode.ERROR);
              } else {
                SpanStatusExtractor.getDefault()
                    .extract(spanStatusBuilder, dataFetchingEnvironment, result, error);
              }
            })
        .setEnabled(enabled)
        .buildInstrumenter();
  }

  private GraphqlInstrumenterFactory() {}
}
