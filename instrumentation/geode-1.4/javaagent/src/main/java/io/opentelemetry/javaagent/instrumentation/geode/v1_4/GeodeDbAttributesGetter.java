/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQueryAnalyzer;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

final class GeodeDbAttributesGetter implements DbClientAttributesGetter<GeodeRequest, Void> {

  // "String literals are delimited by single quotation marks."
  // https://geode.apache.org/docs/guide/114/developing/query_additional/literals.html
  private static final SqlDialect OQL_DIALECT =
      SqlDialect.builder().setDoubleQuotesAreIdentifiers(true).build();

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
  public String getDbQueryText(GeodeRequest request) {
    // Geode uses OQL, but SQL sanitization is still useful to mask literals.
    return analyzer.analyze(request.getQueryText(), OQL_DIALECT).getQueryText();
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
    ServerTarget target = request.getServerTarget();
    return target == null ? null : target.getAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(GeodeRequest request) {
    ServerTarget target = request.getServerTarget();
    return target == null ? null : target.getPort();
  }
}
