/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import io.opentelemetry.instrumentation.api.db.SqlStatementSanitizer;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.semconv.SemanticAttributes;
import javax.annotation.Nullable;

final class GeodeDbAttributesGetter implements DbClientAttributesGetter<GeodeRequest> {

  private static final SqlStatementSanitizer sanitizer =
      SqlStatementSanitizer.create(CommonConfig.get().isStatementSanitizationEnabled());

  @Override
  public String getSystem(GeodeRequest request) {
    return SemanticAttributes.DbSystemValues.GEODE;
  }

  @Override
  @Nullable
  public String getUser(GeodeRequest request) {
    return null;
  }

  @Override
  public String getName(GeodeRequest request) {
    return request.getRegion().getName();
  }

  @Override
  @Nullable
  public String getConnectionString(GeodeRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getStatement(GeodeRequest request) {
    // sanitized statement is cached
    return sanitizer.sanitize(request.getQuery()).getFullStatement();
  }

  @Override
  @Nullable
  public String getOperation(GeodeRequest request) {
    return request.getOperation();
  }
}
