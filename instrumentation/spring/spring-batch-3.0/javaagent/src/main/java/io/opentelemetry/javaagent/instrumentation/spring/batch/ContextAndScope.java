/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public final class ContextAndScope {
  private final Context context;
  private final Scope scope;

  public ContextAndScope(Context context, Scope scope) {
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
