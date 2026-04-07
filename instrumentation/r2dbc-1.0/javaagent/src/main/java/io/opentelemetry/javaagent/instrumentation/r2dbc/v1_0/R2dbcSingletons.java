/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.shaded.R2dbcTelemetry;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.shaded.R2dbcTelemetryBuilder;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.shaded.internal.Experimental;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.shaded.internal.R2dbcSqlAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.sqlcommenter.SqlCommenterCustomizerHolder;

public class R2dbcSingletons {

  private static final R2dbcTelemetry telemetry;

  static {
    R2dbcTelemetryBuilder builder =
        R2dbcTelemetry.builder(GlobalOpenTelemetry.get())
            .setQuerySanitizationEnabled(
                DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "r2dbc"))
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(
                    new R2dbcSqlAttributesGetter(), GlobalOpenTelemetry.get()));
    Experimental.setSqlCommenterEnabled(
        builder, DbConfig.isSqlCommenterEnabled(GlobalOpenTelemetry.get(), "r2dbc"));
    Experimental.customizeSqlCommenter(
        builder,
        sqlCommenterBuilder ->
            SqlCommenterCustomizerHolder.getCustomizer().customize(sqlCommenterBuilder));
    telemetry = builder.build();
  }

  public static R2dbcTelemetry telemetry() {
    return telemetry;
  }

  private R2dbcSingletons() {}
}
