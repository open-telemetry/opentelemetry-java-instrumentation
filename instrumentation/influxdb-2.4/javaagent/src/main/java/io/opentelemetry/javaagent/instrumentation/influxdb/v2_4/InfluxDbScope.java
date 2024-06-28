package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.CallDepth;

public class InfluxDbScope {
  private final CallDepth callDepth;
  private final InfluxDbRequest influxDbRequest;
  private final Context context;
  private final Scope scope;

  private InfluxDbScope(
      CallDepth callDepth, InfluxDbRequest influxDbRequest, Context context, Scope scope) {
    this.callDepth = callDepth;
    this.influxDbRequest = influxDbRequest;
    this.context = context;
    this.scope = scope;
  }

  public static InfluxDbScope start(
      Instrumenter<InfluxDbRequest, Void> instrumenter,
      CallDepth callDepth,
      Context parentContext,
      InfluxDbRequest influxDbRequest) {
    Context context = instrumenter.start(parentContext, influxDbRequest);
    return new InfluxDbScope(callDepth, influxDbRequest, context, context.makeCurrent());
  }

  public static void end(
      Object scope, Instrumenter<InfluxDbRequest, Void> instrumenter, Throwable throwable) {
    if (!(scope instanceof InfluxDbScope)) {
      return;
    }
    InfluxDbScope influxDbScope = (InfluxDbScope) scope;

    if (influxDbScope.callDepth.decrementAndGet() > 0) {
      return;
    }

    if (influxDbScope.scope == null) {
      return;
    }

    influxDbScope.scope.close();

    instrumenter.end(influxDbScope.context, influxDbScope.influxDbRequest, null, throwable);
  }
}
