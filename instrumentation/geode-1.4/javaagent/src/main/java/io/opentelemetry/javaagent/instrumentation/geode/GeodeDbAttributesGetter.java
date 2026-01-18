/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementInfo;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class GeodeDbAttributesGetter implements DbClientAttributesGetter<GeodeRequest, Void> {

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
    SqlStatementInfo sqlStatementInfo = request.getSqlStatementInfo();
    return sqlStatementInfo != null ? sqlStatementInfo.getQueryText() : null;
  }

  @Override
  @Nullable
  public String getDbQuerySummary(GeodeRequest request) {
    SqlStatementInfo sqlStatementInfo = request.getSqlStatementInfo();
    return sqlStatementInfo != null ? sqlStatementInfo.getQuerySummary() : null;
  }

  @Override
  @Nullable
  public String getDbOperationName(GeodeRequest request) {
    return request.getOperation();
  }
}
