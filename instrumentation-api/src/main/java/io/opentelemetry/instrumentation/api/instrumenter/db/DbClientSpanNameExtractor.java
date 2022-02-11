/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import javax.annotation.Nullable;

public final class DbClientSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to DB semantic
   * conventions: {@code <db.operation> <db.name>.<table>}.
   *
   * @see DbClientAttributesGetter#operation(Object) used to extract {@code <db.operation>}.
   * @see DbClientAttributesGetter#name(Object) used to extract {@code <db.name>}.
   * @see SqlClientAttributesGetter#table(Object) used to extract {@code <db.table>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      DbClientAttributesGetter<REQUEST> getter) {
    return new DbClientSpanNameExtractor<>(getter);
  }

  private static final String DEFAULT_SPAN_NAME = "DB Query";

  private final DbClientAttributesGetter<REQUEST> getter;

  private DbClientSpanNameExtractor(DbClientAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  @Override
  public String extract(REQUEST request) {
    String operation = getter.operation(request);
    String dbName = getter.name(request);
    if (operation == null) {
      return dbName == null ? DEFAULT_SPAN_NAME : dbName;
    }

    String table = getTableName(request);
    StringBuilder name = new StringBuilder(operation);
    if (dbName != null || table != null) {
      name.append(' ');
    }
    // skip db name if table already has a db name prefixed to it
    if (dbName != null && (table == null || table.indexOf('.') == -1)) {
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
    if (getter instanceof SqlClientAttributesGetter) {
      return ((SqlClientAttributesGetter<REQUEST>) getter).table(request);
    }
    return null;
  }
}
