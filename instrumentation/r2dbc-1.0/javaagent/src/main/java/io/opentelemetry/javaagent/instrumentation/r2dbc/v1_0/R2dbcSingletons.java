/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.shaded.R2dbcTelemetry;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.shaded.R2dbcTelemetryBuilder;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.shaded.internal.Experimental;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.shaded.internal.R2dbcSqlAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.sqlcommenter.SqlCommenterCustomizerHolder;

public final class R2dbcSingletons {

  private static final R2dbcTelemetry TELEMETRY;

  static {
    R2dbcTelemetryBuilder builder =
        R2dbcTelemetry.builder(GlobalOpenTelemetry.get())
            .setStatementSanitizationEnabled(
                DeclarativeConfigUtil.getBoolean(
                        GlobalOpenTelemetry.get(),
                        "java",
                        "r2dbc",
                        "statement_sanitizer",
                        "enabled")
                    .or(
                        () ->
                            DeclarativeConfigUtil.getBoolean(
                                GlobalOpenTelemetry.get(),
                                "java",
                                "db",
                                "statement_sanitizer",
                                "enabled"))
                    .orElse(true))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    R2dbcSqlAttributesGetter.INSTANCE,
                    PeerServiceResolver.create(GlobalOpenTelemetry.get())));
    Experimental.setEnableSqlCommenter(
        builder,
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "r2dbc",
                "experimental",
                "sqlcommenter",
                "enabled")
            .or(
                () ->
                    DeclarativeConfigUtil.getBoolean(
                        GlobalOpenTelemetry.get(),
                        "java",
                        "common",
                        "db",
                        "sqlcommenter/development"))
            .orElse(false));
    Experimental.customizeSqlCommenter(
        builder,
        sqlCommenterBuilder ->
            SqlCommenterCustomizerHolder.getCustomizer().customize(sqlCommenterBuilder));
    TELEMETRY = builder.build();
  }

  public static R2dbcTelemetry telemetry() {
    return TELEMETRY;
  }

  private R2dbcSingletons() {}
}
