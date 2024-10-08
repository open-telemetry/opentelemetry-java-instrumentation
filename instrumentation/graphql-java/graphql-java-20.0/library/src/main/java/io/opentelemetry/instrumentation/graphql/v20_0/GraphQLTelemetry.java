/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.DataFetchingEnvironment;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.graphql.internal.OpenTelemetryInstrumentationHelper;

@SuppressWarnings({"AbbreviationAsWordInName", "MemberName"})
public final class GraphQLTelemetry {

  /** Returns a new {@link GraphQLTelemetry} configured with the given {@link OpenTelemetry}. */
  public static GraphQLTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link GraphQLTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static GraphQLTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new GraphQLTelemetryBuilder(openTelemetry);
  }

  private final OpenTelemetryInstrumentationHelper helper;
  private final Instrumenter<DataFetchingEnvironment, Void> dataFetcherInstrumenter;
  private final boolean createSpansForTrivialDataFetcher;

  GraphQLTelemetry(
      OpenTelemetry openTelemetry,
      boolean sanitizeQuery,
      Instrumenter<DataFetchingEnvironment, Void> dataFetcherInstrumenter,
      boolean createSpansForTrivialDataFetcher) {
    helper = GraphqlInstrumenterFactory.createInstrumentationHelper(openTelemetry, sanitizeQuery);
    this.dataFetcherInstrumenter = dataFetcherInstrumenter;
    this.createSpansForTrivialDataFetcher = createSpansForTrivialDataFetcher;
  }

  /**
   * Returns a new {@link Instrumentation} that generates telemetry for received GraphQL requests.
   */
  public Instrumentation newInstrumentation() {
    return new OpenTelemetryInstrumentation(
        helper, dataFetcherInstrumenter, createSpansForTrivialDataFetcher);
  }
}
