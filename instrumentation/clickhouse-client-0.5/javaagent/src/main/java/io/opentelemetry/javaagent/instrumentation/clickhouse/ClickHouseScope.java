/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import static io.opentelemetry.javaagent.instrumentation.clickhouse.ClickHouseSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

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
      Context parentContext, ClickHouseDbRequest clickHouseDbRequest) {
    if (!instrumenter().shouldStart(parentContext, clickHouseDbRequest)) {
      return null;
    }

    Context context = instrumenter().start(parentContext, clickHouseDbRequest);
    return new ClickHouseScope(clickHouseDbRequest, context, context.makeCurrent());
  }

  public void end(Throwable throwable) {
    scope.close();
    instrumenter().end(context, clickHouseDbRequest, null, throwable);
  }
}
