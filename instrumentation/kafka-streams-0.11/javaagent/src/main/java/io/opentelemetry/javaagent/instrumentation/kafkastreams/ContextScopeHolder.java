/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class ContextScopeHolder {
  public static final ThreadLocal<ContextScopeHolder> HOLDER = new ThreadLocal<>();

  private Context context;
  private Scope scope;

  public void closeScope() {
    scope.close();
  }

  public Context getContext() {
    return context;
  }

  public void set(Context context, Scope scope) {
    this.context = context;
    this.scope = scope;
  }
}
