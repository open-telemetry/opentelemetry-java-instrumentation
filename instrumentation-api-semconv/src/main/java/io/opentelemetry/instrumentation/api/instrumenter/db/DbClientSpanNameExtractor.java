/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.instrumentation.api.db.SqlStatementInfo;
import io.opentelemetry.instrumentation.api.db.SqlStatementSanitizer;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public abstract class DbClientSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to DB semantic
   * conventions: {@code <db.operation> <db.name>}.
   *
   * @see DbClientAttributesGetter#operation(Object) used to extract {@code <db.operation>}.
   * @see DbClientAttributesGetter#name(Object) used to extract {@code <db.name>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      DbClientAttributesGetter<REQUEST> getter) {
    return new GenericDbClientSpanNameExtractor<>(getter);
  }

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to DB semantic
   * conventions: {@code <db.operation> <db.name>.<table>}.
   *
   * @see SqlStatementInfo#getOperation() used to extract {@code <db.operation>}.
   * @see DbClientAttributesGetter#name(Object) used to extract {@code <db.name>}.
   * @see SqlStatementInfo#getTable() used to extract {@code <db.table>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      SqlClientAttributesGetter<REQUEST> getter) {
    return new SqlClientSpanNameExtractor<>(getter);
  }

  private static final String DEFAULT_SPAN_NAME = "DB Query";

  private DbClientSpanNameExtractor() {}

  protected String computeSpanName(String dbName, String operation, String table) {
    if (operation == null) {
      return dbName == null ? DEFAULT_SPAN_NAME : dbName;
    }

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

  private static final class GenericDbClientSpanNameExtractor<REQUEST>
      extends DbClientSpanNameExtractor<REQUEST> {

    private final DbClientAttributesGetter<REQUEST> getter;

    private GenericDbClientSpanNameExtractor(DbClientAttributesGetter<REQUEST> getter) {
      this.getter = getter;
    }

    @Override
    public String extract(REQUEST request) {
      String dbName = getter.name(request);
      String operation = getter.operation(request);
      return computeSpanName(dbName, operation, null);
    }
  }

  private static final class SqlClientSpanNameExtractor<REQUEST>
      extends DbClientSpanNameExtractor<REQUEST> {

    private final SqlClientAttributesGetter<REQUEST> getter;

    private SqlClientSpanNameExtractor(SqlClientAttributesGetter<REQUEST> getter) {
      this.getter = getter;
    }

    @Override
    public String extract(REQUEST request) {
      String dbName = getter.name(request);
      SqlStatementInfo sanitizedStatement =
          SqlStatementSanitizer.sanitize(getter.rawStatement(request));
      return computeSpanName(
          dbName, sanitizedStatement.getOperation(), sanitizedStatement.getTable());
    }
  }
}
