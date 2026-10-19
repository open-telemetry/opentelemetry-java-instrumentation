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
import org.influxdb.dto.QueryResult;
import retrofit2.Call;
import retrofit2.Response;

public final class InfluxDbQueryState {
  public static final ContextKey<InfluxDbQueryState> KEY =
      ContextKey.named("opentelemetry-influxdb-query");

  private final InfluxDbQuery request;
  private final Context context;
  private final AtomicBoolean ended = new AtomicBoolean();
  @Nullable private Call<?> call;
  @Nullable private Throwable callbackError;
  @Nullable private volatile String errorType;

  public InfluxDbQueryState(Context parentContext, InfluxDbQuery request) {
    this.request = request;
    context = queryInstrumenter().start(parentContext, request).with(KEY, this);
  }

  public Context getContext() {
    return context;
  }

  public void setCall(Call<?> call) {
    this.call = call;
  }

  public void observe(@Nullable QueryResult result) {
    // Canceling a blocked chunk read produces a synthetic error result, not a query failure.
    if ((call == null || !call.isCanceled()) && InfluxDbResponse.hasError(result)) {
      errorType = "_OTHER";
    }
  }

  void onCallbackError(Throwable error) {
    // Newer clients catch callback exceptions and cancel the call before onResponse returns.
    if (call == null || !call.isCanceled()) {
      callbackError = error;
    }
  }

  void onFailure(Throwable error) {
    // The client cancels after decoding failures too; only canceled reads are non-failures.
    if (!(error instanceof IOException) || call == null || !call.isCanceled()) {
      callbackError = error;
    }
  }

  public void endResponse(Response<?> response, @Nullable Throwable error, boolean canceled) {
    if (!response.isSuccessful()) {
      errorType = Integer.toString(response.code());
    }
    Object body = response.body();
    if (body instanceof QueryResult) {
      observe((QueryResult) body);
    }
    end(canceled ? null : error);
  }

  public void end(@Nullable Throwable error) {
    if (ended.compareAndSet(false, true)) {
      call = null;
      queryInstrumenter()
          .end(
              context,
              request,
              new InfluxDbResponse(errorType),
              error != null ? error : callbackError);
    }
  }
}
