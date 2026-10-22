/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbSingletons.queryInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import javax.annotation.Nullable;
import okhttp3.HttpUrl;
import org.influxdb.dto.Query;
import org.influxdb.impl.InfluxDBImpl;
import retrofit2.Retrofit;

public class InfluxDbQueryAdviceScope {
  private final CallDepth callDepth;
  @Nullable private final Context parentContext;
  @Nullable private final InfluxDbQueryState queryState;
  @Nullable private final Scope scope;

  private InfluxDbQueryAdviceScope(
      CallDepth callDepth,
      @Nullable Context parentContext,
      @Nullable InfluxDbQueryState queryState,
      @Nullable Scope scope) {
    this.callDepth = callDepth;
    this.parentContext = parentContext;
    this.queryState = queryState;
    this.scope = scope;
  }

  public static InfluxDbQueryAdviceScope start(
      @Nullable Query query, Retrofit retrofit, boolean asynchronous) {
    CallDepth callDepth = CallDepth.forClass(InfluxDBImpl.class);
    if (callDepth.getAndIncrement() > 0) {
      try {
        Context current = Context.current();
        InfluxDbQueryState currentState = current.get(InfluxDbQueryState.KEY);
        Scope scope =
            currentState != null && !(asynchronous && currentState.belongsToAsyncQuery(retrofit))
                ? current.with(InfluxDbQueryState.KEY, null).makeCurrent()
                : null;
        return new InfluxDbQueryAdviceScope(callDepth, null, null, scope);
      } catch (Throwable t) {
        callDepth.decrementAndGet();
        throw t;
      }
    }
    if (query == null) {
      return new InfluxDbQueryAdviceScope(callDepth, null, null, null);
    }

    Context parentContext =
        Context.current().with(InfluxDbQueryState.RESPONSE_PROCESSING_STATE, null);
    HttpUrl httpUrl = retrofit.baseUrl();
    InfluxDbQuery influxDbQuery =
        InfluxDbQuery.create(
            httpUrl.host(), httpUrl.port(), query.getDatabase(), query.getCommand());
    if (!queryInstrumenter().shouldStart(parentContext, influxDbQuery)) {
      return new InfluxDbQueryAdviceScope(callDepth, null, null, null);
    }

    InfluxDbQueryState queryState =
        new InfluxDbQueryState(parentContext, influxDbQuery, retrofit, asynchronous);
    return new InfluxDbQueryAdviceScope(
        callDepth, parentContext, queryState, queryState.getContext().makeCurrent());
  }

  @Nullable
  public Context parentContext() {
    return parentContext;
  }

  @Nullable
  public InfluxDbQueryState queryState() {
    return queryState;
  }

  public void end(@Nullable Throwable throwable) {
    int depth = callDepth.decrementAndGet();
    if (scope != null) {
      scope.close();
    }
    if (depth == 0 && queryState != null) {
      queryState.end(throwable);
    }
  }

  public void endAsync(@Nullable Throwable throwable) {
    int depth = callDepth.decrementAndGet();
    if (scope != null) {
      scope.close();
    }
    if (depth == 0 && queryState != null && throwable != null) {
      queryState.end(throwable);
    }
  }
}
