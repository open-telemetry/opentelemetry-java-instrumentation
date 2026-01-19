/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
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
    if (request.getSqlStatementInfo() == null) {
      return null;
    }
    return request.getSqlStatementInfo().getQueryText();
  }

  @Nullable
  @Override
  public String getDbQuerySummary(GeodeRequest request) {
    if (request.getSqlStatementInfo() == null) {
      return null;
    }
    return request.getSqlStatementInfo().getQuerySummary();
  }

  @Override
  @Nullable
  public String getDbOperationName(GeodeRequest request) {
    return request.getOperation();
  }
}
