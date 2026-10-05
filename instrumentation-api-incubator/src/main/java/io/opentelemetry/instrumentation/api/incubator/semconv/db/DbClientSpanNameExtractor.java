/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

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

  private static final String DEFAULT_SPAN_NAME = "DB Query";

  private DbClientSpanNameExtractor() {}

  /**
   * Computes a span name from the operation and first available target.
   *
   * <p>Fallback order:
   *
   * <ol>
   *   <li>{db.operation.name} {target} if both are available
   *   <li>{db.operation.name} if only operation is available
   *   <li>{target} if only target is available
   *   <li>{db.system.name} if neither operation nor target is available
   *   <li>{@code DB Query} if no database system name is available
   * </ol>
   *
   * <p>Target fallback order:
   *
   * <ol>
   *   <li>{db.collection.name}
   *   <li>{db.stored_procedure.name}
   *   <li>{db.namespace}
   *   <li>{server.address}, with {:server.port} appended when the port is available
   * </ol>
   */
  private static <REQUEST> String computeSpanName(
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
      String querySummary = getter.getDbQuerySummary(request);
      if (querySummary != null) {
        return querySummary;
      }
      return computeSpanName(
          getter,
          request,
          getter.getDbOperationName(request),
          getter.getDbCollectionName(request),
          null);
    }
  }

  private static final class SqlClientSpanNameExtractor<REQUEST>
      extends DbClientSpanNameExtractor<REQUEST> {

    private final SqlClientAttributesGetter<REQUEST, ?> getter;

    private SqlClientSpanNameExtractor(SqlClientAttributesGetter<REQUEST, ?> getter) {
      this.getter = getter;
    }

    @SuppressWarnings("deprecation") // SQL analysis supplies collection names for fallback naming
    @Override
    public String extract(REQUEST request) {
      SqlDialect dialect = getter.getSqlDialect(request);
      Collection<String> rawQueryTexts = getter.getRawQueryTexts(request);

      if (rawQueryTexts.isEmpty()) {
        if (isBatch(request)) {
          return "BATCH";
        }
        return computeSpanName(getter, request, null, null, null);
      }

      if (rawQueryTexts.size() == 1) {
        String rawQueryText = rawQueryTexts.iterator().next();
        SqlQuery analyzedQuery = SqlQueryAnalyzerUtil.analyzeWithSummary(rawQueryText, dialect);
        boolean batch = isBatch(request);
        String querySummary = analyzedQuery.getQuerySummary();
        if (querySummary != null) {
          return batch ? "BATCH " + querySummary : querySummary;
        }
        return computeSpanName(
            getter,
            request,
            batch ? "BATCH" : null,
            analyzedQuery.getCollectionName(),
            analyzedQuery.getStoredProcedureName());
      }

      MultiQuery multiQuery = MultiQuery.analyzeWithSummary(rawQueryTexts, dialect);
      String querySummary = multiQuery.getQuerySummary();
      if (querySummary != null) {
        return querySummary;
      }
      return computeSpanName(getter, request, null, null, multiQuery.getStoredProcedureName());
    }

    private boolean isBatch(REQUEST request) {
      Long batchSize = getter.getDbOperationBatchSize(request);
      // Empty batches with size 0 are batches; single-statement batches are reported as non-batch.
      return batchSize != null && batchSize != 1;
    }
  }
}
