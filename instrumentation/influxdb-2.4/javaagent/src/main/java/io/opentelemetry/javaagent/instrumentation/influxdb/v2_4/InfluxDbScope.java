/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Container used to carry state between enter and exit advices */
public class InfluxDbScope {
  private final InfluxDbRequest influxDbRequest;
  private final Context context;
  private final Scope scope;

  private InfluxDbScope(InfluxDbRequest influxDbRequest, Context context, Scope scope) {
    this.influxDbRequest = influxDbRequest;
    this.context = context;
    this.scope = scope;
  }

  public static InfluxDbScope start(
      Instrumenter<InfluxDbRequest, Void> instrumenter,
      Context parentContext,
      InfluxDbRequest influxDbRequest) {
    Context context = instrumenter.start(parentContext, influxDbRequest);
    return new InfluxDbScope(influxDbRequest, context, context.makeCurrent());
  }

  public static void end(
      Object scope, Instrumenter<InfluxDbRequest, Void> instrumenter, Throwable throwable) {
    if (!(scope instanceof InfluxDbScope)) {
      return;
    }
    InfluxDbScope influxDbScope = (InfluxDbScope) scope;

    if (influxDbScope.scope == null) {
      return;
    }

    influxDbScope.scope.close();

    instrumenter.end(influxDbScope.context, influxDbScope.influxDbRequest, null, throwable);
  }
}
