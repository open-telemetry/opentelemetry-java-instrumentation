/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.impl.SqlClientBase;
import java.util.concurrent.CompletableFuture;

public final class VertxSqlClientSingletons {
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
                        AgentCommonConfig.get().isStatementSanitizationEnabled())
                    .build())
            .addAttributesExtractor(
                ServerAttributesExtractor.create(VertxSqlClientNetAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    VertxSqlClientNetAttributesGetter.INSTANCE,
                    AgentCommonConfig.get().getPeerServiceResolver()))
            .addOperationMetrics(DbClientMetrics.get());

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

  private static final VirtualField<Promise<?>, RequestData> requestDataField =
      VirtualField.find(Promise.class, RequestData.class);

  public static void attachRequest(
      Promise<?> promise, VertxSqlClientRequest request, Context context, Context parentContext) {
    requestDataField.set(promise, new RequestData(request, context, parentContext));
  }

  public static Scope endQuerySpan(Promise<?> promise, Throwable throwable) {
    RequestData requestData = requestDataField.get(promise);
    if (requestData == null) {
      return null;
    }
    instrumenter().end(requestData.context, requestData.request, null, throwable);
    return requestData.parentContext.makeCurrent();
  }

  static class RequestData {
    final VertxSqlClientRequest request;
    final Context context;
    final Context parentContext;

    RequestData(VertxSqlClientRequest request, Context context, Context parentContext) {
      this.request = request;
      this.context = context;
      this.parentContext = parentContext;
    }
  }

  // this virtual field is also used in SqlClientBase instrumentation
  private static final VirtualField<SqlClientBase<?>, SqlConnectOptions> connectOptionsField =
      VirtualField.find(SqlClientBase.class, SqlConnectOptions.class);

  public static Future<SqlConnection> attachConnectOptions(
      Future<SqlConnection> future, SqlConnectOptions connectOptions) {
    return future.map(
        sqlConnection -> {
          if (sqlConnection instanceof SqlClientBase) {
            connectOptionsField.set((SqlClientBase<?>) sqlConnection, connectOptions);
          }
          return sqlConnection;
        });
  }

  public static <T> Future<T> wrapContext(Future<T> future) {
    Context context = Context.current();
    CompletableFuture<T> result = new CompletableFuture<>();
    future
        .toCompletionStage()
        .whenComplete(
            (value, throwable) -> {
              try (Scope ignore = context.makeCurrent()) {
                if (throwable != null) {
                  result.completeExceptionally(throwable);
                } else {
                  result.complete(value);
                }
              }
            });
    return Future.fromCompletionStage(result);
  }

  private VertxSqlClientSingletons() {}
}
