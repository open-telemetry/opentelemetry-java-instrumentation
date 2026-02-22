/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.Collection;
import javax.annotation.Nullable;

public abstract class DbClientSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to DB semantic
   * conventions.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      DbClientAttributesGetter<REQUEST, ?> getter) {
    return new GenericDbClientSpanNameExtractor<>(getter);
  }

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to DB semantic
   * conventions.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      SqlClientAttributesGetter<REQUEST, ?> getter) {
    return new SqlClientSpanNameExtractor<>(getter);
  }

  /**
   * Returns a {@link SpanNameExtractor} that uses SQL parsing for stable semconv span names but
   * preserves the old semconv span name format (without collection name) for backward
   * compatibility.
   *
   * <p>This is a transitional method for migrating instrumentations from {@link
   * DbClientAttributesGetter} to {@link SqlClientAttributesGetter}. Once old database semconv are
   * dropped, callers should switch to {@link #create(SqlClientAttributesGetter)}.
   *
   * @deprecated Use {@link #create(SqlClientAttributesGetter)} instead.
   */
  @Deprecated // to be removed in 3.0
  public static <REQUEST> SpanNameExtractor<REQUEST> createForMigration(
      SqlClientAttributesGetter<REQUEST, ?> getter) {
    return new MigratingSqlClientSpanNameExtractor<>(getter);
  }

  private static final String DEFAULT_SPAN_NAME = "DB Query";

  private DbClientSpanNameExtractor() {}

  private static String computeSpanName(
      @Nullable String namespace,
      @Nullable String operationName,
      @Nullable String collectionName,
      @Nullable String storedProcedureName) {
    if (operationName == null) {
      return namespace == null ? DEFAULT_SPAN_NAME : namespace;
    }

    StringBuilder spanName = new StringBuilder(operationName);
    String mainIdentifier = collectionName != null ? collectionName : storedProcedureName;
    if (namespace != null || mainIdentifier != null) {
      spanName.append(' ');
    }
    // skip namespace if identifier already has a namespace prefixed to it
    if (namespace != null && (mainIdentifier == null || mainIdentifier.indexOf('.') == -1)) {
      spanName.append(namespace);
      if (mainIdentifier != null) {
        spanName.append('.');
      }
    }
    if (mainIdentifier != null) {
      spanName.append(mainIdentifier);
    }
    return spanName.toString();
  }

  /**
   * Computes the span name following stable semconv fallback order.
   *
   * <p>Fallback order:
   *
   * <ol>
   *   <li>{db.operation.name} {target} if operation is available
   *   <li>{target} if only target is available
   *   <li>{db.system.name} if nothing else is available
   * </ol>
   *
   * <p>Target fallback order:
   *
   * <ol>
   *   <li>{db.collection.name}
   *   <li>{db.stored_procedure.name}
   *   <li>{db.namespace}
   *   <li>{server.address:server.port}
   * </ol>
   */
  private static <REQUEST> String computeSpanNameStable(
      DbClientAttributesGetter<REQUEST, ?> getter,
      REQUEST request,
      @Nullable String operation,
      @Nullable String collectionName,
      @Nullable String storedProcedureName) {

    String target = collectionName;
    if (target == null) {
      target = storedProcedureName;
    }
    if (target == null) {
      target = getter.getDbNamespace(request);
    }
    if (target == null) {
      String serverAddress = getter.getServerAddress(request);
      if (serverAddress != null) {
        Integer serverPort = getter.getServerPort(request);
        if (serverPort != null) {
          target = serverAddress + ":" + serverPort;
        } else {
          target = serverAddress;
        }
      }
    }

    // Build span name
    if (operation != null) {
      if (target != null) {
        return operation + " " + target;
      }
      return operation;
    }

    // No operation - use target alone
    if (target != null) {
      return target;
    }

    // Final fallback to db.system.name (required attribute per spec)
    String dbSystem = getter.getDbSystemName(request);
    return dbSystem != null ? dbSystem : DEFAULT_SPAN_NAME;
  }

  private static final class GenericDbClientSpanNameExtractor<REQUEST>
      extends DbClientSpanNameExtractor<REQUEST> {

    private final DbClientAttributesGetter<REQUEST, ?> getter;

    private GenericDbClientSpanNameExtractor(DbClientAttributesGetter<REQUEST, ?> getter) {
      this.getter = getter;
    }

    @Override
    public String extract(REQUEST request) {
      if (emitStableDatabaseSemconv()) {
        String querySummary = getter.getDbQuerySummary(request);
        if (querySummary != null) {
          return querySummary;
        }
        String operationName = getter.getDbOperationName(request);
        return computeSpanNameStable(getter, request, operationName, null, null);
      }
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
      SqlDialect dialect = getter.getSqlDialect(request);
      Collection<String> rawQueryTexts = getter.getRawQueryTexts(request);

      if (rawQueryTexts.isEmpty()) {
        if (emitStableDatabaseSemconv()) {
          String querySummary = getter.getDbQuerySummary(request);
          if (querySummary != null) {
            return querySummary;
          }
          String operationName = getter.getDbOperationName(request);
          return computeSpanNameStable(getter, request, operationName, null, null);
        }
        String operationName = getter.getDbOperationName(request);
        return computeSpanName(namespace, operationName, null, null);
      }

      if (!emitStableDatabaseSemconv()) {
        if (rawQueryTexts.size() > 1) { // for backcompat(?)
          return computeSpanName(namespace, null, null, null);
        }
        SqlQuery sanitizedQuery =
            SqlQuerySanitizerUtil.sanitize(rawQueryTexts.iterator().next(), dialect);
        return computeSpanName(
            namespace,
            sanitizedQuery.getOperationName(),
            sanitizedQuery.getCollectionName(),
            sanitizedQuery.getStoredProcedureName());
      }

      if (rawQueryTexts.size() == 1) {
        String rawQueryText = rawQueryTexts.iterator().next();
        SqlQuery sanitizedQuery = SqlQuerySanitizerUtil.sanitizeWithSummary(rawQueryText, dialect);
        boolean batch = isBatch(request);
        String querySummary = sanitizedQuery.getQuerySummary();
        if (querySummary != null) {
          return batch ? "BATCH " + querySummary : querySummary;
        }
        return computeSpanNameStable(
            getter, request, batch ? "BATCH" : null, null, sanitizedQuery.getStoredProcedureName());
      }

      MultiQuery multiQuery = MultiQuery.analyzeWithSummary(rawQueryTexts, dialect, false);
      String querySummary = multiQuery.getQuerySummary();
      if (querySummary != null) {
        return querySummary;
      }
      return computeSpanNameStable(
          getter, request, null, null, multiQuery.getStoredProcedureName());
    }

    private boolean isBatch(REQUEST request) {
      Long batchSize = getter.getDbOperationBatchSize(request);
      return batchSize != null && batchSize > 1;
    }
  }

  /**
   * A transitional span name extractor that uses SQL parsing for stable semconv but preserves the
   * old semconv span name format (operation + namespace, without collection name) for backward
   * compatibility during migration from {@link DbClientAttributesGetter} to {@link
   * SqlClientAttributesGetter}.
   */
  private static final class MigratingSqlClientSpanNameExtractor<REQUEST>
      extends DbClientSpanNameExtractor<REQUEST> {

    private final SqlClientAttributesGetter<REQUEST, ?> getter;
    private final SqlClientSpanNameExtractor<REQUEST> sqlDelegate;

    private MigratingSqlClientSpanNameExtractor(SqlClientAttributesGetter<REQUEST, ?> getter) {
      this.getter = getter;
      this.sqlDelegate = new SqlClientSpanNameExtractor<>(getter);
    }

    @Override
    public String extract(REQUEST request) {
      if (emitStableDatabaseSemconv()) {
        return sqlDelegate.extract(request);
      }
      // For old semconv, use the generic span name format (operation + namespace)
      // without collection name to preserve backward compatibility
      String namespace = getter.getDbNamespace(request);
      Collection<String> rawQueryTexts = getter.getRawQueryTexts(request);
      String operationName = null;
      if (rawQueryTexts.size() == 1) {
        String rawQuery = rawQueryTexts.iterator().next();
        SqlDialect dialect = getter.getSqlDialect(request);
        SqlQuery sanitizedQuery = SqlQuerySanitizerUtil.sanitize(rawQuery, dialect);
        operationName = sanitizedQuery.getOperationName();
      }
      if (operationName == null) {
        operationName = getter.getDbOperationName(request);
      }
      return computeSpanName(namespace, operationName, null, null);
    }
  }
}
