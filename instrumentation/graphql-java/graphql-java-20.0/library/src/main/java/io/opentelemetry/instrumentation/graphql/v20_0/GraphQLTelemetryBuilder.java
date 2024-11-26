/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link GraphQLTelemetry}. */
@SuppressWarnings({"AbbreviationAsWordInName", "MemberName"})
public final class GraphQLTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean sanitizeQuery = true;

  private boolean dataFetcherInstrumentationEnabled = false;

  private boolean trivialDataFetcherInstrumentationEnabled = false;

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
  public GraphQLTelemetryBuilder setDataFetcherInstrumentationEnabled(
      boolean dataFetcherInstrumentationEnabled) {
    this.dataFetcherInstrumentationEnabled = dataFetcherInstrumentationEnabled;
    return this;
  }

  /**
   * Sets whether spans are created for trivial GraphQL Data Fetchers. A trivial DataFetcher is one
   * that simply maps data from an object to a field. Default is {@code false}.
   */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setTrivialDataFetcherInstrumentationEnabled(
      boolean trivialDataFetcherInstrumentationEnabled) {
    this.trivialDataFetcherInstrumentationEnabled = trivialDataFetcherInstrumentationEnabled;
    return this;
  }

  /**
   * Returns a new {@link GraphQLTelemetry} with the settings of this {@link
   * GraphQLTelemetryBuilder}.
   */
  public GraphQLTelemetry build() {
    return new GraphQLTelemetry(
        openTelemetry,
        sanitizeQuery,
        GraphqlInstrumenterFactory.createDataFetcherInstrumenter(
            openTelemetry, dataFetcherInstrumentationEnabled),
        trivialDataFetcherInstrumentationEnabled);
  }
}
