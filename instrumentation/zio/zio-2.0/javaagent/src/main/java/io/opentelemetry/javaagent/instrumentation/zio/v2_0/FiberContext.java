/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.FiberContextStorageHolder;
import io.opentelemetry.javaagent.bootstrap.FiberContextStorageHolder.Activation;
import io.opentelemetry.javaagent.bootstrap.FiberContextStorageHolder.ContextState;
import javax.annotation.Nullable;

class FiberContext implements ContextState {
  private Context context;
  @Nullable private Object scope;
  @Nullable private Activation activation;
  private boolean ended;

  private FiberContext(Context context) {
    this.context = context;
  }

  static FiberContext create() {
    return new FiberContext(Context.current());
  }

  boolean onSuspend() {
    if (ended) {
      while (activation != null) {
        activation = FiberContextStorageHolder.deactivate(activation);
      }
      return true;
    }
    if (activation != null) {
      activation = FiberContextStorageHolder.deactivate(activation);
    }
    return false;
  }

  void onResume() {
    activation = FiberContextStorageHolder.activate(this, activation);
  }

  void onEnd() {
    ended = true;
  }

  @Override
  public Object getContext() {
    return context;
  }

  @Override
  public void setContext(Object context) {
    this.context = (Context) requireNonNull(context);
  }

  @Nullable
  @Override
  public Object getScope() {
    return scope;
  }

  @Override
  public void setScope(@Nullable Object scope) {
    this.scope = scope;
  }
}
