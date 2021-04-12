/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongoasync.v3_3;

import com.mongodb.async.SingleResultCallback;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class SingleResultCallbackWrapper implements SingleResultCallback<Object> {
  private final Context context;
  private final SingleResultCallback<Object> delegate;

  public SingleResultCallbackWrapper(Context context, SingleResultCallback<Object> delegate) {
    this.context = context;
    this.delegate = delegate;
  }

  @Override
  public void onResult(Object server, Throwable throwable) {
    try (Scope ignored = context.makeCurrent()) {
      delegate.onResult(server, throwable);
    }
  }
}
