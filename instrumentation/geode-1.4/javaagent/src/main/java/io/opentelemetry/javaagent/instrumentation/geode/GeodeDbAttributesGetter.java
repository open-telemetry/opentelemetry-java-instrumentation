/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementSanitizer;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class GeodeDbAttributesGetter implements DbClientAttributesGetter<GeodeRequest, Void> {

  private static final SqlStatementSanitizer sanitizer =
      SqlStatementSanitizer.create(
          DeclarativeConfigUtil.getBoolean(
                  GlobalOpenTelemetry.get(), "java", "common", "db", "statement_sanitizer", "enabled")
              .orElse(true));

  @SuppressWarnings("deprecation") // using deprecated DbSystemIncubatingValues
  @Override
  public String getDbSystem(GeodeRequest request) {
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
    // sanitized statement is cached
    return sanitizer.sanitize(request.getQuery()).getFullStatement();
  }

  @Override
  @Nullable
  public String getDbOperationName(GeodeRequest request) {
    return request.getOperation();
  }
}
