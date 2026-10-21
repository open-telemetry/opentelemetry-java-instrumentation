/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import javax.annotation.Nullable;
import okhttp3.HttpUrl;
import org.influxdb.dto.Query;
import org.influxdb.impl.InfluxDBImpl;
import retrofit2.Retrofit;

public final class InfluxDbQueryAdviceHelper {

  @Nullable
  public static State start(@Nullable Query query, Retrofit retrofit) {
    CallDepth callDepth = CallDepth.forClass(InfluxDBImpl.class);
    if (callDepth.getAndIncrement() > 0 || query == null) {
      return null;
    }

    Context parentContext = Context.current();
    HttpUrl httpUrl = retrofit.baseUrl();
    InfluxDbQuery influxDbQuery =
        InfluxDbQuery.create(
            httpUrl.host(), httpUrl.port(), query.getDatabase(), query.getCommand());
    if (!InfluxDbSingletons.queryInstrumenter().shouldStart(parentContext, influxDbQuery)) {
      return null;
    }

    return new State(
        parentContext,
        InfluxDbScope.start(InfluxDbSingletons.queryInstrumenter(), parentContext, influxDbQuery));
  }

  public static void end(@Nullable Throwable throwable, @Nullable State state) {
    CallDepth callDepth = CallDepth.forClass(InfluxDBImpl.class);
    if (callDepth.decrementAndGet() > 0 || state == null) {
      return;
    }

    state.scope.end(throwable);
  }

  public static final class State {
    private final Context parentContext;
    private final InfluxDbScope<InfluxDbQuery> scope;

    private State(Context parentContext, InfluxDbScope<InfluxDbQuery> scope) {
      this.parentContext = parentContext;
      this.scope = scope;
    }

    public Context parentContext() {
      return parentContext;
    }
  }

  private InfluxDbQueryAdviceHelper() {}
}
