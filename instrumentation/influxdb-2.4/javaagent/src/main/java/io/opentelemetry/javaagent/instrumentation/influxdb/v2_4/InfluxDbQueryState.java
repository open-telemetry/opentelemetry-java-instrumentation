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
  private final boolean asynchronous;
  private final AtomicBoolean callbackAssociated = new AtomicBoolean();
  private final AtomicBoolean ended = new AtomicBoolean();
  @Nullable private Call<?> call;
  @Nullable private Throwable callbackError;
  @Nullable private volatile String errorType;

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

  public void observe(@Nullable QueryResult result) {
    if (callbackError == null
        && errorType == null
        && (call == null || !call.isCanceled())
        && InfluxDbResponse.hasError(result)) {
      errorType = "_OTHER";
    }
  }

  void onCallbackError(Throwable error) {
    callbackError = error;
  }

  public void onFailure(Throwable error) {
    if (callbackError != null) {
      return;
    }

    Throwable failure = error;
    // Older supported clients do not have InfluxDBException.
    if (error.getClass().getName().equals("org.influxdb.InfluxDBException")
        && error.getCause() != null) {
      failure = error.getCause();
    }
    // Malformed JSON remains a query failure even if the call is canceled.
    if (failure instanceof IOException
        && !failure.getClass().getName().equals("com.squareup.moshi.JsonEncodingException")
        && call != null
        && call.isCanceled()) {
      return;
    }
    callbackError = error;
  }

  public void endResponse(Response<?> response, @Nullable Throwable error) {
    // The synchronous query API does not expose HTTP responses, so status alone cannot be
    // classified consistently.
    Object body = response.body();
    if (body instanceof QueryResult) {
      observe((QueryResult) body);
    }
    if (error != null) {
      onFailure(error);
    }
    end(null);
  }

  public void end(@Nullable Throwable error) {
    if (ended.compareAndSet(false, true)) {
      call = null;
      queryInstrumenter()
          .end(
              context,
              request,
              new InfluxDbResponse(errorType),
              callbackError != null ? callbackError : error);
    }
  }
}
