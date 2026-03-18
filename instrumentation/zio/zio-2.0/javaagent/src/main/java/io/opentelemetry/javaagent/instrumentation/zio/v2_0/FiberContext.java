/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;

public final class FiberContext {
  private Context context;
  @Nullable private Context initialContext;
  @Nullable private Scope scope;

  private FiberContext(Context context) {
    this.context = context;
  }

  public static FiberContext create() {
    return new FiberContext(Context.current());
  }

  public void onSuspend() {
    context = Context.current();

    // First we try closing the scope that was opened in onResume. This may fail if user code has
    // left an open scope because only the latest scope can be closed.
    // See https://github.com/open-telemetry/opentelemetry-java/issues/5303
    requireNonNull(scope).close();

    // If the current context doesn't match the initial context then there must be an open scope,
    // reset the state to the initial context.
    if (Context.current() != initialContext) {
      requireNonNull(initialContext).makeCurrent();
    }
  }

  public void onResume() {
    initialContext = Context.current();
    scope = context.makeCurrent();
  }
}
