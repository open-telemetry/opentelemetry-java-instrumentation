/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientDeferredRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil;
import io.vertx.core.Promise;

public final class VertxSqlClientQueryState {
  public static final ContextKey<VertxSqlClientQueryState> QUERY_STATE =
      ContextKey.named("vertx-sql-client-query");

  private final VertxSqlClientDeferredRequest request;
  private final Promise<?> promise;
  private final Context context;

  public VertxSqlClientQueryState(
      VertxSqlClientDeferredRequest request, Promise<?> promise, Context context) {
    this.request = request;
    this.promise = promise;
    this.context = context.with(QUERY_STATE, null);
  }

  public void capture(VertxSqlClientInfo info) {
    request.replaceInfo(info);
  }

  public Context getContext() {
    return context;
  }

  public void end(Throwable throwable) {
    Scope parentScope = VertxSqlClientUtil.endQuerySpan(instrumenter(), promise, throwable);
    if (parentScope != null) {
      parentScope.close();
    }
  }
}
