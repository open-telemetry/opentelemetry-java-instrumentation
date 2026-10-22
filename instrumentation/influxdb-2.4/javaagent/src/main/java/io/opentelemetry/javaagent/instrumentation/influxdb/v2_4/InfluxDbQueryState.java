/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbSingletons.queryInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import retrofit2.Call;

public final class InfluxDbQueryState {
  public static final ContextKey<InfluxDbQueryState> KEY =
      ContextKey.named("opentelemetry-influxdb-query");

  private final InfluxDbQuery request;
  private final Context context;
  private final boolean asynchronous;
  private final AtomicBoolean callbackAssociated = new AtomicBoolean();
  private final AtomicBoolean ended = new AtomicBoolean();
  @Nullable private Call<?> call;
  @Nullable private Throwable callbackError;

  public InfluxDbQueryState(Context parentContext, InfluxDbQuery request, boolean asynchronous) {
    this.request = request;
    this.asynchronous = asynchronous;
    context = queryInstrumenter().start(parentContext, request).with(KEY, this);
  }

  boolean isAsynchronous() {
    return asynchronous;
  }

  public boolean associateCallback() {
    return asynchronous && callbackAssociated.compareAndSet(false, true);
  }

  public Context getContext() {
    return context;
  }

  public void setCall(Call<?> call) {
    this.call = call;
  }

  void onCallbackError(Throwable error) {
    callbackError = error;
  }

  public void onFailure(Throwable error) {
    boolean jsonEncodingFailure =
        error.getClass().getName().equals("com.squareup.moshi.JsonEncodingException")
            || (error.getClass().getName().equals("org.influxdb.InfluxDBException")
                && error.getCause() != null
                && error
                    .getCause()
                    .getClass()
                    .getName()
                    .equals("com.squareup.moshi.JsonEncodingException"));
    boolean readFailure =
        !jsonEncodingFailure
            && (error instanceof IOException
                || (error.getClass().getName().equals("org.influxdb.InfluxDBException")
                    && error.getCause() instanceof IOException));
    if (callbackError == null && (!readFailure || call == null || !call.isCanceled())) {
      callbackError = error;
    }
  }

  public void end(@Nullable Throwable error) {
    if (ended.compareAndSet(false, true)) {
      call = null;
      queryInstrumenter()
          .end(context, request, null, callbackError != null ? callbackError : error);
    }
  }
}
