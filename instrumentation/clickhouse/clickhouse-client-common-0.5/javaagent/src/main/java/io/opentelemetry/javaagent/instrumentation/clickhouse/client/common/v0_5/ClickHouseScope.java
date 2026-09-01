/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Container used to carry state between enter and exit advices */
public class ClickHouseScope {
  private static final ContextKey<ClickHouseDbRequest> REQUEST_KEY =
      ContextKey.named("clickhouse-db-request");

  private final ClickHouseDbRequest clickHouseDbRequest;
  private final Context context;
  private final Scope scope;
  private final Instrumenter<ClickHouseDbRequest, Void> instrumenter;

  private ClickHouseScope(
      ClickHouseDbRequest clickHouseDbRequest,
      Context context,
      Scope scope,
      Instrumenter<ClickHouseDbRequest, Void> instrumenter) {
    this.clickHouseDbRequest = clickHouseDbRequest;
    this.context = context;
    this.scope = scope;
    this.instrumenter = instrumenter;
  }

  @Nullable
  public static ClickHouseScope start(
      Instrumenter<ClickHouseDbRequest, Void> instrumenter,
      Context parentContext,
      ClickHouseDbRequest clickHouseDbRequest) {
    if (!instrumenter.shouldStart(parentContext, clickHouseDbRequest)) {
      return null;
    }

    Context context =
        instrumenter
            .start(parentContext, clickHouseDbRequest)
            .with(REQUEST_KEY, clickHouseDbRequest);
    return new ClickHouseScope(clickHouseDbRequest, context, context.makeCurrent(), instrumenter);
  }

  @Nullable
  public static ClickHouseDbRequest currentRequest() {
    return Context.current().get(REQUEST_KEY);
  }

  public void endOnCompletion(CompletableFuture<?> future) {
    scope.close();
    future.whenComplete(
        (result, throwable) ->
            instrumenter.end(context, clickHouseDbRequest, null, unwrap(throwable)));
  }

  public void end(@Nullable Throwable throwable) {
    scope.close();
    instrumenter.end(context, clickHouseDbRequest, null, throwable);
  }

  @Nullable
  private static Throwable unwrap(@Nullable Throwable throwable) {
    if ((throwable instanceof CompletionException || throwable instanceof ExecutionException)
        && throwable.getCause() != null) {
      return throwable.getCause();
    }
    return throwable;
  }
}
