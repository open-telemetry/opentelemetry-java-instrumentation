/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.vertx.sqlclient.SqlConnectOptions;
import java.util.Map;

public final class VertxSqlClientSingletons {
  public static final String OTEL_REQUEST_KEY = "otel.request";
  public static final String OTEL_CONTEXT_KEY = "otel.context";
  public static final String OTEL_PARENT_CONTEXT_KEY = "otel.parent-context";
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-sql-client-4.0";
  private static final Instrumenter<VertxSqlClientRequest, Void> INSTRUMENTER;
  private static final ThreadLocal<SqlConnectOptions> connectOptions = new ThreadLocal<>();

  static {
    SpanNameExtractor<VertxSqlClientRequest> spanNameExtractor =
        DbClientSpanNameExtractor.create(VertxSqlClientAttributesGetter.INSTANCE);

    InstrumenterBuilder<VertxSqlClientRequest, Void> builder =
        Instrumenter.<VertxSqlClientRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(VertxSqlClientAttributesGetter.INSTANCE)
                    .setStatementSanitizationEnabled(
                        CommonConfig.get().isStatementSanitizationEnabled())
                    .build())
            .addAttributesExtractor(
                NetClientAttributesExtractor.create(VertxSqlClientNetAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    VertxSqlClientNetAttributesGetter.INSTANCE,
                    CommonConfig.get().getPeerServiceMapping()));

    INSTRUMENTER = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<VertxSqlClientRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static void setSqlConnectOptions(SqlConnectOptions sqlConnectOptions) {
    connectOptions.set(sqlConnectOptions);
  }

  public static SqlConnectOptions getSqlConnectOptions() {
    return connectOptions.get();
  }

  public static Scope endQuerySpan(Map<Object, Object> contextData, Throwable throwable) {
    VertxSqlClientRequest otelRequest =
        (VertxSqlClientRequest) contextData.remove(OTEL_REQUEST_KEY);
    Context otelContext = (Context) contextData.remove(OTEL_CONTEXT_KEY);
    Context otelParentContext = (Context) contextData.remove(OTEL_PARENT_CONTEXT_KEY);
    if (otelRequest == null || otelContext == null || otelParentContext == null) {
      return null;
    }
    instrumenter().end(otelContext, otelRequest, null, throwable);
    return otelParentContext.makeCurrent();
  }

  private VertxSqlClientSingletons() {}
}
