/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public final class DbSemanticConventions {
  private static final String DB_QUERY = "DB Query";

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to DB semantic
   * conventions: {@code <db.operation> <db.name><table>}.
   *
   * @see DbAttributesExtractor#dbOperation(Object) used to extract {@code <db.operation>}.
   * @see DbAttributesExtractor#dbName(Object) used to extract {@code <db.name>}.
   * @see SqlAttributesExtractor#dbTable(Object) used to extract {@code <db.table>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> spanName(
      DbAttributesExtractor<REQUEST> attributesExtractor) {
    return spanName(attributesExtractor, request -> DB_QUERY);
  }

  /**
   * A helper method for constructing the span name formatting according to DB semantic conventions:
   * {@code <db.operation> <db.name><table>}. If {@code dbName} and {@code operation} are not
   * provided then {@code defaultSpanNameExtractor} is used to compute the span name.
   *
   * @see DbAttributesExtractor#dbOperation(Object) used to extract {@code <db.operation>}.
   * @see DbAttributesExtractor#dbName(Object) used to extract {@code <db.name>}.
   * @see SqlAttributesExtractor#dbTable(Object) used to extract {@code <db.table>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> spanName(
      DbAttributesExtractor<REQUEST> attributesExtractor,
      SpanNameExtractor<REQUEST> defaultSpanNameExtractor) {
    return new DbSpanNameExtractor<>(attributesExtractor, defaultSpanNameExtractor);
  }

  private DbSemanticConventions() {}
}
