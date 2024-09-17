/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementSanitizer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class GeodeDbAttributesGetter implements DbClientAttributesGetter<GeodeRequest> {

  private static final SqlStatementSanitizer sanitizer =
      SqlStatementSanitizer.create(AgentCommonConfig.get().isStatementSanitizationEnabled());

  @Override
  public String getSystem(GeodeRequest request) {
    return DbIncubatingAttributes.DbSystemValues.GEODE;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(GeodeRequest request) {
    return null;
  }

  @Deprecated
  @Override
  public String getName(GeodeRequest request) {
    return request.getRegion().getName();
  }

  @Nullable
  @Override
  public String getDbNamespace(GeodeRequest request) {
    return request.getRegion().getName();
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(GeodeRequest request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getStatement(GeodeRequest request) {
    // sanitized statement is cached
    return sanitizer.sanitize(request.getQuery()).getFullStatement();
  }

  @Nullable
  @Override
  public String getDbQueryText(GeodeRequest request) {
    // sanitized statement is cached
    return sanitizer.sanitize(request.getQuery()).getFullStatement();
  }

  @Deprecated
  @Override
  @Nullable
  public String getOperation(GeodeRequest request) {
    return request.getOperation();
  }

  @Nullable
  @Override
  public String getDbOperationName(GeodeRequest request) {
    return request.getOperation();
  }
}
