/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v12_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link GraphQLTelemetry}. */
@SuppressWarnings({"AbbreviationAsWordInName", "MemberName"})
public final class GraphQLTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean sanitizeQuery = true;
  private boolean addOperationNameToSpanName = false;

  GraphQLTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Sets whether sensitive information should be removed from queries. Default is {@code true}. */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setSanitizeQuery(boolean sanitizeQuery) {
    this.sanitizeQuery = sanitizeQuery;
    return this;
  }

  /**
   * Sets whether GraphQL operation name is added to the span name. Default is {@code false}.
   *
   * <p>WARNING: GraphQL operation name is provided by the client and can have high cardinality. Use
   * only when the server is not exposed to malicious clients.
   */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setAddOperationNameToSpanName(boolean addOperationNameToSpanName) {
    this.addOperationNameToSpanName = addOperationNameToSpanName;
    return this;
  }

  /**
   * Returns a new {@link GraphQLTelemetry} with the settings of this {@link
   * GraphQLTelemetryBuilder}.
   */
  public GraphQLTelemetry build() {
    return new GraphQLTelemetry(openTelemetry, sanitizeQuery, addOperationNameToSpanName);
  }
}
