/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql;

import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link GraphQLTelemetry}. */
@SuppressWarnings("AbbreviationAsWordInName")
public final class GraphQLTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean captureExperimentalSpanAttributes;
  private boolean sanitizeQuery = true;

  GraphQLTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions.
   */
  public GraphQLTelemetryBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /** Sets whether sensitive information should be removed from queries. Default is {@code true}. */
  public GraphQLTelemetryBuilder setSanitizeQuery(boolean sanitizeQuery) {
    this.sanitizeQuery = sanitizeQuery;
    return this;
  }

  /**
   * Returns a new {@link GraphQLTelemetry} with the settings of this {@link
   * GraphQLTelemetryBuilder}.
   */
  public GraphQLTelemetry build() {
    return new GraphQLTelemetry(openTelemetry, captureExperimentalSpanAttributes, sanitizeQuery);
  }
}
