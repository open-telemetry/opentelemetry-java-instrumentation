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

  protected String computeSpanName(
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
  protected String computeSpanNameStable(
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

      if (rawQueryTexts.size() == 1) {
        SqlStatementInfo sanitizedStatement =
            SqlStatementSanitizerUtil.sanitize(rawQueryTexts.iterator().next());
        String querySummary = sanitizedStatement.getQuerySummary();
        if (querySummary != null) {
          return isBatch(request) ? "BATCH " + querySummary : querySummary;
        }
        String operationName = sanitizedStatement.getOperationName();
        if (isBatch(request)) {
          operationName = operationName == null ? "BATCH" : "BATCH " + operationName;
        }
        return computeSpanNameStable(
            getter,
            request,
            operationName,
            sanitizedStatement.getCollectionName(),
            sanitizedStatement.getStoredProcedureName());
      }

      MultiQuery multiQuery = MultiQuery.analyze(rawQueryTexts, false);
      String querySummary = multiQuery.getQuerySummary();
      if (querySummary != null) {
        return querySummary;
      }
      return computeSpanNameStable(
          getter,
          request,
          multiQuery.getOperationName(),
          multiQuery.getCollectionName(),
          multiQuery.getStoredProcedureName());
    }

    private boolean isBatch(REQUEST request) {
      Long batchSize = getter.getDbOperationBatchSize(request);
      return batchSize != null && batchSize > 1;
    }
  }
}
