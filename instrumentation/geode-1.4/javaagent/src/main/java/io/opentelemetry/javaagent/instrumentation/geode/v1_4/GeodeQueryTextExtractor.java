/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class GeodeQueryTextExtractor implements AttributesExtractor<GeodeRequest, Void> {

  private final AttributesExtractor<GeodeRequest, Void> sqlExtractor =
      SqlClientAttributesExtractor.builder(new GeodeSqlAttributesGetter())
          .setQuerySanitizationEnabled(
              DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "geode"))
          .build();

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, GeodeRequest request) {
    if (request.getQueryText() == null) {
      return;
    }
    // OQL can use SQL literal masking, but its region paths and operations are not SQL.
    // Keep the Geode operation and collection rather than the SQL-derived summary.
    AttributesBuilder sqlAttributes = Attributes.builder();
    sqlExtractor.onStart(sqlAttributes, parentContext, request);
    attributes.put(DB_QUERY_TEXT, sqlAttributes.build().get(DB_QUERY_TEXT));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      GeodeRequest request,
      @Nullable Void response,
      @Nullable Throwable error) {}
}
