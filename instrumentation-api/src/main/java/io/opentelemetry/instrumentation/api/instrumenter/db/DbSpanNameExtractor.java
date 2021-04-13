/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class DbSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {
  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to DB semantic
   * conventions: {@code <db.operation> <db.name><table>}.
   *
   * @see DbAttributesExtractor#dbOperation(Object) used to extract {@code <db.operation>}.
   * @see DbAttributesExtractor#dbName(Object) used to extract {@code <db.name>}.
   * @see SqlAttributesExtractor#dbTable(Object) used to extract {@code <db.table>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      DbAttributesExtractor<REQUEST> attributesExtractor) {
    return new DbSpanNameExtractor<>(attributesExtractor);
  }

  private static final String DEFAULT_SPAN_NAME = "DB Query";

  private final DbAttributesExtractor<REQUEST> attributesExtractor;

  private DbSpanNameExtractor(DbAttributesExtractor<REQUEST> attributesExtractor) {
    this.attributesExtractor = attributesExtractor;
  }

  @Override
  public String extract(REQUEST request) {
    String operation = attributesExtractor.dbOperation(request);
    String dbName = attributesExtractor.dbName(request);
    if (operation == null) {
      return dbName == null ? DEFAULT_SPAN_NAME : dbName;
    }

    String table = getTableName(request);
    StringBuilder name = new StringBuilder(operation);
    if (dbName != null || table != null) {
      name.append(' ');
    }
    if (dbName != null) {
      name.append(dbName);
      if (table != null) {
        name.append('.');
      }
    }
    if (table != null) {
      name.append(table);
    }
    return name.toString();
  }

  @Nullable
  private String getTableName(REQUEST request) {
    if (attributesExtractor instanceof SqlAttributesExtractor) {
      return ((SqlAttributesExtractor<REQUEST>) attributesExtractor).dbTable(request);
    }
    return null;
  }
}
