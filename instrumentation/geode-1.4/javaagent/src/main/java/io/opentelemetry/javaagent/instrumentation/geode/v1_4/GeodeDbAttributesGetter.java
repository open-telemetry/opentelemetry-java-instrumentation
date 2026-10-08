/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQueryAnalyzer;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

final class GeodeDbAttributesGetter implements DbClientAttributesGetter<GeodeRequest, Void> {

  private static final SqlQueryAnalyzer analyzer =
      SqlQueryAnalyzer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "geode"));

  @Override
  @Nullable
  public String getErrorType(
      GeodeRequest request, @Nullable Void response, @Nullable Throwable error) {
    return null;
  }

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
  public String getDbQueryText(GeodeRequest request) {
    // Geode query language (OQL) is very different from SQL
    // but SQL sanitization is still useful to mask literals
    // "String literals are delimited by single quotation marks."
    // https://geode.apache.org/docs/guide/114/developing/query_additional/literals.html
    return analyzer.analyze(request.getQueryText(), DOUBLE_QUOTES_ARE_IDENTIFIERS).getQueryText();
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
    return request.getOperationName();
  }

  @Override
  @Nullable
  public String getServerAddress(GeodeRequest request) {
    DbServerTarget target = request.getServerTarget();
    return target == null ? null : target.getAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(GeodeRequest request) {
    DbServerTarget target = request.getServerTarget();
    return target == null ? null : target.getPort();
  }
}
