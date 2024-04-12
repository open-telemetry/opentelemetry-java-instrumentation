/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;
import javax.sql.DataSource;

enum DataSourceDbAttributesExtractor implements AttributesExtractor<DataSource, DbInfo> {
  INSTANCE;

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, DataSource dataSource) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      DataSource dataSource,
      @Nullable DbInfo dbInfo,
      @Nullable Throwable error) {
    if (dbInfo == null) {
      return;
    }
    internalSet(attributes, DbIncubatingAttributes.DB_SYSTEM, dbInfo.getSystem());
    internalSet(attributes, DbIncubatingAttributes.DB_USER, dbInfo.getUser());
    internalSet(attributes, DbIncubatingAttributes.DB_NAME, getName(dbInfo));
  }

  private static String getName(DbInfo dbInfo) {
    String name = dbInfo.getName();
    return name == null ? dbInfo.getDb() : name;
  }
}
