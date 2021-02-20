/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class WrappedFutureListener implements GenericFutureListener {
  private final Context context;
  private final GenericFutureListener delegate;

  public WrappedFutureListener(Context context, GenericFutureListener delegate) {
    this.context = context;
    this.delegate = delegate;
  }

  @Override
  public void operationComplete(Future future) throws Exception {
    try (Scope ignored = context.makeCurrent()) {
      delegate.operationComplete(future);
    }
  }
}
