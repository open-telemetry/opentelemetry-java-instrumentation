/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.DataFetchingEnvironment;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

@SuppressWarnings("AbbreviationAsWordInName")
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

  private final Instrumenter<OpenTelemetryInstrumentationState, ExecutionResult>
      executionInstrumenter;

  private final Instrumenter<DataFetchingEnvironment, Void> dataFetcherInstrumenter;

  private final boolean sanitizeQuery;

  private final boolean createSpansForTrivialDataFetcher;

  GraphQLTelemetry(
      Instrumenter<OpenTelemetryInstrumentationState, ExecutionResult> executionInstrumenter,
      boolean sanitizeQuery,
      Instrumenter<DataFetchingEnvironment, Void> dataFetcherInstrumenter,
      boolean createSpansForTrivialDataFetcher) {
    this.executionInstrumenter = executionInstrumenter;
    this.sanitizeQuery = sanitizeQuery;
    this.dataFetcherInstrumenter = dataFetcherInstrumenter;
    this.createSpansForTrivialDataFetcher = createSpansForTrivialDataFetcher;
  }

  /**
   * Returns a new {@link Instrumentation} that generates telemetry for received GraphQL requests.
   */
  public Instrumentation newInstrumentation() {
    return new OpenTelemetryInstrumentation(
        executionInstrumenter,
        sanitizeQuery,
        dataFetcherInstrumenter,
        createSpansForTrivialDataFetcher);
  }
}
