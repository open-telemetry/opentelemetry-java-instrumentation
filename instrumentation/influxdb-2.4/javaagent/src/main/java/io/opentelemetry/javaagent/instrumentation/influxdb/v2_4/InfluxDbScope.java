/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;

/** Container used to carry state between enter and exit advices */
public class InfluxDbScope<REQUEST> {
  private final Instrumenter<REQUEST, Void> instrumenter;
  private final REQUEST request;
  private final Context context;
  private final Scope scope;

  private InfluxDbScope(
      Instrumenter<REQUEST, Void> instrumenter,
      REQUEST request,
      Context context,
      Scope scope) {
    this.instrumenter = instrumenter;
    this.request = request;
    this.context = context;
    this.scope = scope;
  }

  public static <REQUEST> InfluxDbScope<REQUEST> start(
      Instrumenter<REQUEST, Void> instrumenter,
      Context parentContext,
      REQUEST request) {
    Context context = instrumenter.start(parentContext, request);
    return new InfluxDbScope<>(instrumenter, request, context, context.makeCurrent());
  }

  public void end(@Nullable Throwable throwable) {
    scope.close();

    instrumenter.end(context, request, null, throwable);
  }
}
