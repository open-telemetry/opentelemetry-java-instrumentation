/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import io.opentelemetry.instrumentation.api.db.SqlStatementSanitizer;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class GeodeDbAttributesGetter implements DbClientAttributesGetter<GeodeRequest> {

  @Override
  public String system(GeodeRequest request) {
    return SemanticAttributes.DbSystemValues.GEODE;
  }

  @Override
  @Nullable
  public String user(GeodeRequest request) {
    return null;
  }

  @Override
  public String name(GeodeRequest request) {
    return request.getRegion().getName();
  }

  @Override
  @Nullable
  public String connectionString(GeodeRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String statement(GeodeRequest request) {
    // sanitized statement is cached
    return SqlStatementSanitizer.sanitize(request.getQuery()).getFullStatement();
  }

  @Override
  @Nullable
  public String operation(GeodeRequest request) {
    return request.getOperation();
  }
}
