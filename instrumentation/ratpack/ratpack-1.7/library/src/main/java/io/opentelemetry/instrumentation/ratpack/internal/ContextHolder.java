/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.internal;

import io.opentelemetry.context.Context;
import ratpack.http.client.RequestSpec;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ContextHolder {
  private final Context context;
  private final RequestSpec requestSpec;

  public ContextHolder(Context context, RequestSpec requestSpec) {
    this.context = context;
    this.requestSpec = requestSpec;
  }

  public Context context() {
    return context;
  }

  public RequestSpec requestSpec() {
    return requestSpec;
  }
}
