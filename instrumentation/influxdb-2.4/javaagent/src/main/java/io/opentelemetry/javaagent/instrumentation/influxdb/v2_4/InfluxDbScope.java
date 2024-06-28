package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;

public class InfluxDbScope {
  public final CallDepth callDepth;
  public final InfluxDbRequest influxDbRequest;
  public final Context context;
  public final Scope scope;

  public InfluxDbScope(
      CallDepth callDepth, InfluxDbRequest influxDbRequest, Context context, Scope scope) {
    this.callDepth = callDepth;
    this.influxDbRequest = influxDbRequest;
    this.context = context;
    this.scope = scope;
  }
}
