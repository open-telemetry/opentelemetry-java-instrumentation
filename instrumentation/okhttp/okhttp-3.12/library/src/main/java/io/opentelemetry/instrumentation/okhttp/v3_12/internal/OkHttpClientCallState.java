/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_12.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Response;

/**
 * Per-call state attached to the okhttp {@link okhttp3.Request} as a {@linkplain
 * okhttp3.Request.Builder#tag(Class, Object) tag}. Tying the state to the request keeps it bound to
 * the lifetime of the call: it is reclaimed together with the request and call, so no external map
 * (and thus no weak references or risk of leaks) is needed to associate state with a call.
 *
 * <p>The span is ended once both the okhttp call has ended ({@code callEnd}/{@code callFailed}) and
 * the application interceptor chain has completed, so that timings recorded up to {@code callEnd}
 * and call-level errors that okhttp only surfaces above the {@code EventListener} (e.g. too many
 * redirects) are both captured.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class OkHttpClientCallState {

  private final Context parentContext;
  private final boolean captureTimings;

  @Nullable private Instrumenter<Call, Response> instrumenter;
  @Nullable private Context context;
  private long startNanos;
  @Nullable private Connection connection;
  @Nullable private Response response;
  @Nullable private Throwable networkError;
  @Nullable private Throwable applicationError;
  private boolean networkEnded;
  private boolean applicationEnded;
  private boolean spanEnded;

  public OkHttpClientCallState(Context parentContext, boolean captureTimings) {
    this.parentContext = parentContext;
    this.captureTimings = captureTimings;
  }

  @Nullable
  public static OkHttpClientCallState get(Call call) {
    return call.request().tag(OkHttpClientCallState.class);
  }

  boolean captureTimings() {
    return captureTimings;
  }

  void startSpan(Instrumenter<Call, Response> instrumenter, Call call) {
    if (!instrumenter.shouldStart(parentContext, call)) {
      return;
    }
    this.instrumenter = instrumenter;
    startNanos = System.nanoTime();
    context = instrumenter.start(parentContext, call);
  }

  @Nullable
  Context context() {
    return context;
  }

  long startNanos() {
    return startNanos;
  }

  @Nullable
  Connection connection() {
    return connection;
  }

  void setConnection(Connection connection) {
    this.connection = connection;
  }

  void setResponse(Response response) {
    this.response = response;
  }

  synchronized void networkComplete(Call call, @Nullable Throwable error) {
    networkError = error;
    networkEnded = true;
    endIfReady(call);
  }

  synchronized void applicationComplete(Call call, @Nullable Throwable error) {
    applicationError = error;
    applicationEnded = true;
    endIfReady(call);
  }

  private void endIfReady(Call call) {
    if (context == null || instrumenter == null || spanEnded || !applicationEnded) {
      return;
    }
    // When capturing network timings, wait for callEnd/callFailed so the response-body phase is
    // recorded. Otherwise end as soon as the application interceptor chain completes, so the span
    // is always exported even if the caller never reads/closes the response body (in which case
    // okhttp never fires callEnd).
    if (captureTimings && !networkEnded) {
      return;
    }
    spanEnded = true;
    Throwable error = applicationError != null ? applicationError : networkError;
    instrumenter.end(context, call, response, error);
  }
}
