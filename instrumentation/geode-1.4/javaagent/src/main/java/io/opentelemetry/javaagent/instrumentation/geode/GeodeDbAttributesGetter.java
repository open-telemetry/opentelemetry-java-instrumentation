/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQueryAnalyzer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

final class GeodeDbAttributesGetter implements DbClientAttributesGetter<GeodeRequest, Void> {

  private static final SqlQueryAnalyzer analyzer =
      SqlQueryAnalyzer.create(AgentCommonConfig.get().isQuerySanitizationEnabled());

  @Override
  public String getDbSystemName(GeodeRequest request) {
    return DbSystemNameIncubatingValues.GEODE;
  }

  @Override
  @Nullable
  public String getDbNamespace(GeodeRequest request) {
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
      return analyzer.analyzeWithSummary(request.getQueryText()).getQueryText();
    } else {
      return analyzer.analyze(request.getQueryText()).getQueryText();
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
    return request.getOperationName();
  }
}
