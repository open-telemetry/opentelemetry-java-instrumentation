/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.context;

import io.opentelemetry.context.Context;

public class ContextWithParent {

  private final Context context;
  private final Context parentContext;

  public ContextWithParent(Context context, Context parentContext) {
    this.context = context;
    this.parentContext = parentContext;
  }

  public Context getContext() {
    return context;
  }

  public Context getParentContext() {
    return parentContext;
  }
}
