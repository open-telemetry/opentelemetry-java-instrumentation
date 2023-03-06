/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/** A helper that keeps track of the count of the HTTP request resend attempts. */
public final class HttpClientResend {

  private static final ContextKey<HttpClientResend> KEY =
      ContextKey.named("opentelemetry-http-client-resend-key");
  private static final AtomicIntegerFieldUpdater<HttpClientResend> resendsUpdater =
      AtomicIntegerFieldUpdater.newUpdater(HttpClientResend.class, "resends");

  /**
   * Initializes the HTTP request resend counter.
   *
   * <p>Note that this must be called on a {@code context} that is the parent of all the outgoing
   * HTTP request send attempts; this class is meant to be used before the {@link Instrumenter} is
   * used, so that the resend counter is shared across all the resends.
   */
  public static Context initialize(Context context) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(KEY, new HttpClientResend());
  }

  static int getAndIncrement(Context context) {
    HttpClientResend resend = context.get(KEY);
    if (resend == null) {
      return 0;
    }
    return resendsUpdater.getAndIncrement(resend);
  }

  private volatile int resends = 0;

  private HttpClientResend() {}
}
