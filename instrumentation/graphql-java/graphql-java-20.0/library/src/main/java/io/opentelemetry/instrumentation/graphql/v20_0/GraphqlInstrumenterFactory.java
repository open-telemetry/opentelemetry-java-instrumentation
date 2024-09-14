/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import graphql.schema.DataFetchingEnvironment;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.graphql.internal.OpenTelemetryInstrumentationHelper;

final class GraphqlInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.graphql-java-20.0";

  static OpenTelemetryInstrumentationHelper createInstrumentationHelper(
      OpenTelemetry openTelemetry, boolean sanitizeQuery) {
    return OpenTelemetryInstrumentationHelper.create(
        openTelemetry, INSTRUMENTATION_NAME, sanitizeQuery);
  }

  static Instrumenter<DataFetchingEnvironment, Void> createDataFetcherInstrumenter(
      OpenTelemetry openTelemetry, boolean enabled) {
    return Instrumenter.<DataFetchingEnvironment, Void>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            environment -> environment.getExecutionStepInfo().getField().getName())
        .addAttributesExtractor(new GraphqlDataFetcherAttributesExtractor())
        .setEnabled(enabled)
        .buildInstrumenter();
  }

  private GraphqlInstrumenterFactory() {}
}
