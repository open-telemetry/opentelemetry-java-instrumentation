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
    String mainIdentifier = collectionName != null ? collectionName : storedProcedureName;

    if (operation == null) {
      return dbName == null ? DEFAULT_SPAN_NAME : dbName;
    }

    StringBuilder name = new StringBuilder(operation);
    if (dbName != null || mainIdentifier != null) {
      name.append(' ');
    }
    // skip db name if identifier already has a db name prefixed to it
    if (dbName != null && (mainIdentifier == null || mainIdentifier.indexOf('.') == -1)) {
      name.append(dbName);
      if (mainIdentifier != null) {
        name.append('.');
      }
    }
    if (mainIdentifier != null) {
      name.append(mainIdentifier);
    }
    return name.toString();
  }

  /**
   * Computes the span name following stable semconv fallback order.
   *
   * <p>Fallback order:
   *
   * <ol>
   *   <li>{db.query.summary} if available
   *   <li>{db.operation.name} {target} if operation is available
   *   <li>{target} if only target is available
   *   <li>{db.system.name} if nothing else is available
   * </ol>
   *
   * <p>Target fallback order: collection_name → stored_procedure_name → namespace →
   * server.address:server.port
   */
  protected String computeSpanNameStable(
      DbClientAttributesGetter<REQUEST, ?> getter,
      REQUEST request,
      @Nullable String operation,
      @Nullable String collectionName,
      @Nullable String storedProcedureName) {
    // Determine target following fallback order: collection → stored_procedure → namespace →
    // server:port
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
      String namespace = getter.getDbNamespace(request);
      String operationName = getter.getDbOperationName(request);
      if (SemconvStability.emitStableDatabaseSemconv()) {
        return computeSpanNameStable(getter, request, operationName, null, null);
      }
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
        if (SemconvStability.emitStableDatabaseSemconv()) {
          return computeSpanNameStable(getter, request, null, null, null);
        }
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
        // Fall back to stable semconv span naming
        String operation = sanitizedStatement.getOperationName();
        if (isBatch(request)) {
          operation = operation != null ? "BATCH " + operation : "BATCH";
        }
        return computeSpanNameStable(
            getter,
            request,
            operation,
            sanitizedStatement.getCollectionName(),
            sanitizedStatement.getStoredProcedureName());
      }

      MultiQuery multiQuery = MultiQuery.analyze(rawQueryTexts, false);
      String querySummary = multiQuery.getQuerySummary();
      // Fall back to stable semconv span naming if query summary equals operation (no common table)
      if (!querySummary.equals(multiQuery.getOperationName())) {
        return querySummary;
      }
      return computeSpanNameStable(
          getter, request, multiQuery.getOperationName(), multiQuery.getCollectionName(), null);
    }

    private boolean isBatch(REQUEST request) {
      Long batchSize = getter.getBatchSize(request);
      return batchSize != null && batchSize > 1;
    }
  }
}
