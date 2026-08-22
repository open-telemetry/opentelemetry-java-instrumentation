/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuery;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQueryAnalyzer;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

// the old database semconv hooks are deprecated, as is SqlQuery.getOperationName()
@SuppressWarnings("deprecation")
final class GeodeDbAttributesGetter implements DbClientAttributesGetter<GeodeRequest, Void> {

  // Region.query(String), Region.existsValue(String) and Region.selectValue(String) all run a
  // select: either the OQL query they are given, or "SELECT * FROM <region> this WHERE <predicate>"
  // when they are given a bare query predicate
  private static final String FALLBACK_OPERATION_NAME = "SELECT";

  private static final SqlQueryAnalyzer analyzer =
      SqlQueryAnalyzer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "geode"));

  @Override
  public String getDbSystemName(GeodeRequest request) {
    return DbSystemNameIncubatingValues.GEODE;
  }

  @Override
  @Nullable
  public String getDbNamespace(GeodeRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbCollectionName(GeodeRequest request) {
    return request.getRegion().getName();
  }

  @Override
  @Nullable
  // Old database semconv still uses db.name, so we must implement the deprecated hook.
  public String getDbName(GeodeRequest request) {
    return request.getRegion().getName();
  }

  @Override
  @Nullable
  public String getDbQueryText(GeodeRequest request) {
    // Geode query language (OQL) is very different from SQL
    // but SQL sanitization is still useful to mask literals
    if (emitStableDatabaseSemconv()) {
      // even though not using the summary, this will use the same
      // sanitization logic that will be the default under 3.0
      return analyzeWithSummary(request).getQueryText();
    } else {
      // "String literals are delimited by single quotation marks."
      // https://geode.apache.org/docs/guide/114/developing/query_additional/literals.html
      return analyzer.analyze(request.getQueryText(), DOUBLE_QUOTES_ARE_IDENTIFIERS).getQueryText();
    }
  }

  @Override
  @Nullable
  public String getDbQuerySummary(GeodeRequest request) {
    // Geode query language (OQL) is too different from SQL
    // for SQL summarization to work well
    return null;
  }

  @Override
  @Nullable
  public String getDbOperationName(GeodeRequest request) {
    if (request.getQueryText() == null) {
      return request.getOperationName();
    }
    String operationName = analyzeWithSummary(request).getOperationName();
    return operationName != null ? operationName : FALLBACK_OPERATION_NAME;
  }

  @Override
  @Nullable
  // Old database semconv still uses db.operation, so we must implement the deprecated hook.
  public String getDbOperation(GeodeRequest request) {
    return request.getOperationName();
  }

  // the analyzer caches its results for queries below its large-query threshold, so callers can
  // analyze the same query more than once
  private static SqlQuery analyzeWithSummary(GeodeRequest request) {
    // "String literals are delimited by single quotation marks."
    // https://geode.apache.org/docs/guide/114/developing/query_additional/literals.html
    return analyzer.analyzeWithSummary(request.getQueryText(), DOUBLE_QUOTES_ARE_IDENTIFIERS);
  }
}
