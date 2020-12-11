/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class ContextScopePair {
  private final Context context;
  private final Scope scope;

  public ContextScopePair(Context context, Scope scope) {
    this.context = context;
    this.scope = scope;
  }

  public Context getContext() {
    return context;
  }

  public void closeScope() {
    scope.close();
  }
}
