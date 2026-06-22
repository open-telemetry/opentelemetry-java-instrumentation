/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_12.internal;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Application interceptor that captures exceptions which okhttp surfaces only at the call level —
 * for example the "too many follow-up requests" error thrown by the redirect-following logic, which
 * is not reported through the okhttp {@code EventListener}. The captured exception is read by
 * {@link OkHttpClientCallState} when the client span is ended.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ErrorCapturingInterceptor implements Interceptor {

  @Override
  public Response intercept(Chain chain) throws IOException {
    Throwable error = null;
    try {
      return chain.proceed(chain.request());
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      OkHttpClientCallState state = OkHttpClientCallState.get(chain.call());
      if (state != null) {
        state.applicationComplete(chain.call(), error);
      }
    }
  }
}
