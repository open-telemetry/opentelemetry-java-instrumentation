/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementSanitizer;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class GeodeDbAttributesGetter implements DbClientAttributesGetter<GeodeRequest, Void> {

  private static final SqlStatementSanitizer sanitizer =
      SqlStatementSanitizer.create(AgentCommonConfig.get().isStatementSanitizationEnabled());

  @SuppressWarnings("deprecation") // using deprecated DbSystemIncubatingValues
  @Override
  public String getDbSystemName(GeodeRequest request) {
    return DbIncubatingAttributes.DbSystemIncubatingValues.GEODE;
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
    if (SemconvStability.emitStableDatabaseSemconv()) {
      // even though not using the summary, this will use the same
      // sanitization logic that will be the default under 3.0
      return sanitizer.sanitizeWithSummary(request.getQuery()).getQueryText();
    } else {
      return sanitizer.sanitize(request.getQuery()).getQueryText();
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
    return request.getOperation();
  }
}
