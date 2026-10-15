/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import javax.annotation.Nullable;
import javax.sql.DataSource;

final class DataSourceDbAttributesExtractor implements AttributesExtractor<DataSource, DbInfo> {

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
    attributes.put(DB_NAMESPACE, dbInfo.getDbNamespace());
    attributes.put(DB_SYSTEM_NAME, dbInfo.getDbSystemName());
  }
}
