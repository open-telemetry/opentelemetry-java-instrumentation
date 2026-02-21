/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import javax.annotation.Nullable;
import javax.sql.DataSource;

enum DataSourceDbAttributesExtractor implements AttributesExtractor<DataSource, DbInfo> {
  INSTANCE;

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_NAME = AttributeKey.stringKey("db.name");
  private static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system");
  private static final AttributeKey<String> DB_USER = AttributeKey.stringKey("db.user");
  private static final AttributeKey<String> DB_CONNECTION_STRING =
      AttributeKey.stringKey("db.connection_string");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, DataSource dataSource) {}

  @SuppressWarnings("deprecation") // TODO DbIncubatingAttributes.DB_CONNECTION_STRING deprecation
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
    if (SemconvStability.emitStableDatabaseSemconv()) {
      attributes.put(DB_NAMESPACE, getName(dbInfo));
      attributes.put(DB_SYSTEM_NAME, SemconvStability.stableDbSystemName(dbInfo.getSystem()));
    }
    if (SemconvStability.emitOldDatabaseSemconv()) {
      attributes.put(DB_USER, dbInfo.getUser());
      attributes.put(DB_NAME, getName(dbInfo));
      attributes.put(DB_CONNECTION_STRING, dbInfo.getShortUrl());
      attributes.put(DB_SYSTEM, dbInfo.getSystem());
    }
  }

  private static String getName(DbInfo dbInfo) {
    return dbInfo.getName();
  }
}
