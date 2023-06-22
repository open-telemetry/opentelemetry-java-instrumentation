/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.function.Consumer;

public class TaskWrapper implements Consumer<Object> {
  private final Context context;
  private final Consumer<Object> delegate;

  public TaskWrapper(Context context, Consumer<Object> delegate) {
    this.context = context;
    this.delegate = delegate;
  }

  @Override
  public void accept(Object value) {
    try (Scope ignored = context.makeCurrent()) {
      delegate.accept(value);
    }
  }
}
