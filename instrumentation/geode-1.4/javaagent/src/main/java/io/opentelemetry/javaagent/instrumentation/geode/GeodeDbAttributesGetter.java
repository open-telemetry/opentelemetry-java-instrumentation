/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuerySanitizer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class GeodeDbAttributesGetter implements DbClientAttributesGetter<GeodeRequest, Void> {

  private static final SqlQuerySanitizer sanitizer =
      SqlQuerySanitizer.create(AgentCommonConfig.get().isQuerySanitizationEnabled());

  @Override
  public String getDbSystemName(GeodeRequest request) {
    return DbIncubatingAttributes.DbSystemNameIncubatingValues.GEODE;
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
      //
      // "String literals are delimited by single quotation marks."
      // https://geode.apache.org/docs/guide/114/developing/query_additional/literals.html
      return sanitizer
          .sanitizeWithSummary(request.getQueryText(), DOUBLE_QUOTES_ARE_IDENTIFIERS)
          .getQueryText();
    } else {
      // "String literals are delimited by single quotation marks."
      // https://geode.apache.org/docs/guide/114/developing/query_additional/literals.html
      return sanitizer
          .sanitize(request.getQueryText(), DOUBLE_QUOTES_ARE_IDENTIFIERS)
          .getQueryText();
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
