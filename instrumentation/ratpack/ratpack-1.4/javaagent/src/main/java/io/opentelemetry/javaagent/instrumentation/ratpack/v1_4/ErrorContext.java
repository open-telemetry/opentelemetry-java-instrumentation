/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_4;

import io.opentelemetry.context.Context;

public final class ErrorContext {

  private final Context context;

  public ErrorContext(Context context) {
    this.context = context;
  }

  public Context context() {
    return context;
  }
}
