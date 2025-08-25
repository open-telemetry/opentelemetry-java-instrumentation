/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Container used to carry state between enter and exit advices */
public final class ClickHouseScope {
  private final ClickHouseDbRequest clickHouseDbRequest;
  private final Context context;
  private final Scope scope;

  private ClickHouseScope(ClickHouseDbRequest clickHouseDbRequest, Context context, Scope scope) {
    this.clickHouseDbRequest = clickHouseDbRequest;
    this.context = context;
    this.scope = scope;
  }

  public static ClickHouseScope start(
      Instrumenter<ClickHouseDbRequest, Void> instrumenter,
      Context parentContext,
      ClickHouseDbRequest clickHouseDbRequest) {
    if (!instrumenter.shouldStart(parentContext, clickHouseDbRequest)) {
      return null;
    }

    Context context = instrumenter.start(parentContext, clickHouseDbRequest);
    return new ClickHouseScope(clickHouseDbRequest, context, context.makeCurrent());
  }

  public void end(Instrumenter<ClickHouseDbRequest, Void> instrumenter, Throwable throwable) {
    scope.close();
    instrumenter.end(context, clickHouseDbRequest, null, throwable);
  }
}
