/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import io.opentelemetry.instrumentation.api.db.SqlStatementSanitizer;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class GeodeDbAttributesExtractor extends DbAttributesExtractor<GeodeRequest, Void> {
  @Override
  protected String system(GeodeRequest request) {
    return SemanticAttributes.DbSystemValues.GEODE;
  }

  @Override
  @Nullable
  protected String user(GeodeRequest request) {
    return null;
  }

  @Override
  protected String name(GeodeRequest request) {
    return request.getRegion().getName();
  }

  @Override
  @Nullable
  protected String connectionString(GeodeRequest request) {
    return null;
  }

  @Override
  @Nullable
  protected String statement(GeodeRequest request) {
    // sanitized statement is cached
    return SqlStatementSanitizer.sanitize(request.getQuery()).getFullStatement();
  }

  @Override
  @Nullable
  protected String operation(GeodeRequest request) {
    return request.getOperation();
  }
}
