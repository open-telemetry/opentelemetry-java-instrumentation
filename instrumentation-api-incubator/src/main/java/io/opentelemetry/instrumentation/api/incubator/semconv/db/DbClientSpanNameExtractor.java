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
      @Nullable String namespace, @Nullable String operationName, @Nullable String mainIdentifier) {
    if (operationName == null) {
      return namespace == null ? DEFAULT_SPAN_NAME : namespace;
    }

    StringBuilder spanName = new StringBuilder(operationName);
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
      return computeSpanName(namespace, operationName, null);
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
        return computeSpanName(namespace, null, null);
      }

      if (!SemconvStability.emitStableDatabaseSemconv()) {
        if (rawQueryTexts.size() > 1) { // for backcompat(?)
          return computeSpanName(namespace, null, null);
        }
        SqlStatementInfo sanitizedStatement =
            SqlStatementSanitizerUtil.sanitize(rawQueryTexts.iterator().next());

        return computeSpanName(
            namespace,
            sanitizedStatement.getOperationName(),
            sanitizedStatement.getMainIdentifier());
      }

      if (rawQueryTexts.size() == 1) {
        SqlStatementInfo sanitizedStatement =
            SqlStatementSanitizerUtil.sanitize(rawQueryTexts.iterator().next());
        String operationName = sanitizedStatement.getOperationName();
        if (isBatch(request)) {
          operationName = "BATCH " + operationName;
        }
        return computeSpanName(namespace, operationName, sanitizedStatement.getMainIdentifier());
      }

      MultiQuery multiQuery = MultiQuery.analyze(rawQueryTexts, false);
      return computeSpanName(
          namespace,
          multiQuery.getOperationName() != null
              ? "BATCH " + multiQuery.getOperationName()
              : "BATCH",
          multiQuery.getMainIdentifier());
    }

    private boolean isBatch(REQUEST request) {
      Long batchSize = getter.getBatchSize(request);
      return batchSize != null && batchSize > 1;
    }
  }
}
