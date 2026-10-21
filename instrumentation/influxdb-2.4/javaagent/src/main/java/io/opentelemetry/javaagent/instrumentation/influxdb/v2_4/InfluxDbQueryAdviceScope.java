/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbSingletons.queryInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import javax.annotation.Nullable;
import okhttp3.HttpUrl;
import org.influxdb.dto.Query;
import org.influxdb.impl.InfluxDBImpl;
import retrofit2.Retrofit;

public class InfluxDbQueryAdviceScope {
  private final CallDepth callDepth;
  @Nullable private final Context parentContext;
  @Nullable private final InfluxDbScope<InfluxDbQuery> scope;

  private InfluxDbQueryAdviceScope(
      CallDepth callDepth,
      @Nullable Context parentContext,
      @Nullable InfluxDbScope<InfluxDbQuery> scope) {
    this.callDepth = callDepth;
    this.parentContext = parentContext;
    this.scope = scope;
  }

  public static InfluxDbQueryAdviceScope start(@Nullable Query query, Retrofit retrofit) {
    CallDepth callDepth = CallDepth.forClass(InfluxDBImpl.class);
    if (callDepth.getAndIncrement() > 0 || query == null) {
      return new InfluxDbQueryAdviceScope(callDepth, null, null);
    }

    Context parentContext = Context.current();
    HttpUrl httpUrl = retrofit.baseUrl();
    InfluxDbQuery influxDbQuery =
        InfluxDbQuery.create(
            httpUrl.host(), httpUrl.port(), query.getDatabase(), query.getCommand());
    if (!queryInstrumenter().shouldStart(parentContext, influxDbQuery)) {
      return new InfluxDbQueryAdviceScope(callDepth, null, null);
    }

    return new InfluxDbQueryAdviceScope(
        callDepth,
        parentContext,
        InfluxDbScope.start(queryInstrumenter(), parentContext, influxDbQuery));
  }

  @Nullable
  public Context parentContext() {
    return parentContext;
  }

  public void end(@Nullable Throwable throwable) {
    if (callDepth.decrementAndGet() > 0 || scope == null) {
      return;
    }
    scope.end(throwable);
  }
}
