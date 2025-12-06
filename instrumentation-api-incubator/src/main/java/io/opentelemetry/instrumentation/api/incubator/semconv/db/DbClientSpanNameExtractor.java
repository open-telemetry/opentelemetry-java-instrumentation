/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.Collection;
import javax.annotation.Nullable;

public abstract class DbClientSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to DB semantic
   * conventions: {@code <db.operation> <db.name>}.
   *
   * @see DbClientAttributesGetter#getDbOperationName(Object) used to extract {@code
   *     <db.operation.name>}.
   * @see DbClientAttributesGetter#getDbNamespace(Object) used to extract {@code <db.namespace>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      DbClientAttributesGetter<REQUEST, ?> getter) {
    return new GenericDbClientSpanNameExtractor<>(getter);
  }

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to DB semantic
   * conventions: {@code <db.operation> <db.name>.<identifier>}.
   *
   * @see SqlStatementInfo#getOperationName() used to extract {@code <db.operation.name>}.
   * @see DbClientAttributesGetter#getDbNamespace(Object) used to extract {@code <db.namespace>}.
   * @see SqlStatementInfo#getCollectionName() used to extract table name.
   * @see SqlStatementInfo#getStoredProcedureName() used to extract stored procedure name.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      SqlClientAttributesGetter<REQUEST, ?> getter) {
    return new SqlClientSpanNameExtractor<>(getter);
  }

  private static final String DEFAULT_SPAN_NAME = "DB Query";

  private DbClientSpanNameExtractor() {}

  protected String computeSpanName(
      @Nullable String dbName,
      @Nullable String operation,
      @Nullable String collectionName,
      @Nullable String storedProcedureName) {
    // Use whichever identifier is available (they're mutually exclusive)
    String identifier = collectionName != null ? collectionName : storedProcedureName;

    if (operation == null) {
      return dbName == null ? DEFAULT_SPAN_NAME : dbName;
    }

    StringBuilder name = new StringBuilder(operation);
    if (dbName != null || identifier != null) {
      name.append(' ');
    }
    // skip db name if identifier already has a db name prefixed to it
    if (dbName != null && (identifier == null || identifier.indexOf('.') == -1)) {
      name.append(dbName);
      if (identifier != null) {
        name.append('.');
      }
    }
    if (identifier != null) {
      name.append(identifier);
    }
    return name.toString();
  }

  private static final class GenericDbClientSpanNameExtractor<REQUEST>
      extends DbClientSpanNameExtractor<REQUEST> {

    private final DbClientAttributesGetter<REQUEST, ?> getter;

    private GenericDbClientSpanNameExtractor(DbClientAttributesGetter<REQUEST, ?> getter) {
      this.getter = getter;
    }

    @Override
    public String extract(REQUEST request) {
      String namespace = getter.getDbNamespace(request);
      String operationName = getter.getDbOperationName(request);
      return computeSpanName(namespace, operationName, null, null);
    }
  }

  private static final class SqlClientSpanNameExtractor<REQUEST>
      extends DbClientSpanNameExtractor<REQUEST> {

    private final SqlClientAttributesGetter<REQUEST, ?> getter;

    private SqlClientSpanNameExtractor(SqlClientAttributesGetter<REQUEST, ?> getter) {
      this.getter = getter;
    }

    @Override
    public String extract(REQUEST request) {
      String namespace = getter.getDbNamespace(request);
      Collection<String> rawQueryTexts = getter.getRawQueryTexts(request);

      if (rawQueryTexts.isEmpty()) {
        return computeSpanName(namespace, null, null, null);
      }

      if (!SemconvStability.emitStableDatabaseSemconv()) {
        if (rawQueryTexts.size() > 1) { // for backcompat(?)
          return computeSpanName(namespace, null, null, null);
        }
        SqlStatementInfo sanitizedStatement =
            SqlStatementSanitizerUtil.sanitize(rawQueryTexts.iterator().next());
        return computeSpanName(
            namespace,
            sanitizedStatement.getOperationName(),
            sanitizedStatement.getCollectionName(),
            sanitizedStatement.getStoredProcedureName());
      }

      // For stable semconv, use query summary as span name if available
      if (rawQueryTexts.size() == 1) {
        SqlStatementInfo sanitizedStatement =
            SqlStatementSanitizerUtil.sanitize(rawQueryTexts.iterator().next());
        String querySummary = sanitizedStatement.getQuerySummary();
        if (querySummary != null) {
          if (isBatch(request)) {
            return "BATCH " + querySummary;
          }
          return querySummary;
        }
        // Fall back to old behavior if no query summary
        String operation = sanitizedStatement.getOperationName();
        if (isBatch(request)) {
          operation = "BATCH " + operation;
        }
        return computeSpanName(
            namespace,
            operation,
            sanitizedStatement.getCollectionName(),
            sanitizedStatement.getStoredProcedureName());
      }

      MultiQuery multiQuery = MultiQuery.analyze(rawQueryTexts, false);
      String querySummary = multiQuery.getQuerySummary();
      // Fall back to old behavior if query summary equals operation (no common table)
      if (!querySummary.equals(multiQuery.getOperationName())) {
        return querySummary;
      }
      return computeSpanName(
          namespace, multiQuery.getOperationName(), multiQuery.getCollectionName(), null);
    }

    private boolean isBatch(REQUEST request) {
      Long batchSize = getter.getBatchSize(request);
      return batchSize != null && batchSize > 1;
    }
  }
}
