/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link GraphQLTelemetry}. */
@SuppressWarnings("AbbreviationAsWordInName")
public final class GraphQLTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean sanitizeQuery = true;

  private boolean createSpansForDataFetchers = false;

  private boolean createSpanForTrivialDataFetchers = false;

  GraphQLTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Sets whether sensitive information should be removed from queries. Default is {@code true}. */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setSanitizeQuery(boolean sanitizeQuery) {
    this.sanitizeQuery = sanitizeQuery;
    return this;
  }

  /** Sets whether spans are created for GraphQL Data Fetchers. Default is {@code false}. */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder createSpansForDataFetchers(boolean createSpansForDataFetchers) {
    this.createSpansForDataFetchers = createSpansForDataFetchers;
    return this;
  }

  /** Sets whether spans are created for Trivial GraphQL Data Fetchers. Default is {@code false}. */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder createSpanForTrivialDataFetchers(
      boolean createSpanForTrivialDataFetchers) {
    this.createSpanForTrivialDataFetchers = createSpanForTrivialDataFetchers;
    return this;
  }

  /**
   * Returns a new {@link GraphQLTelemetry} with the settings of this {@link
   * GraphQLTelemetryBuilder}.
   */
  public GraphQLTelemetry build() {
    return new GraphQLTelemetry(
        GraphQLInstrumenterFactory.createExecutionInstrumenter(openTelemetry),
        sanitizeQuery,
        GraphQLInstrumenterFactory.createDataFetcherInstrumenter(
            openTelemetry, createSpansForDataFetchers),
        createSpanForTrivialDataFetchers);
  }
}
