/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;

public class FiberContext {

  private Context context;
  @Nullable private Scope scope;

  private FiberContext(Context context) {
    this.context = context;
    this.scope = null;
  }

  public static FiberContext create() {
    return new FiberContext(Context.current());
  }

  public void onEnd() {
    if (this.scope != null) {
      this.scope.close();
    }
  }

  public void onSuspend() {
    this.context = Context.current();

    if (this.scope != null) {
      this.scope.close();
    }

    this.scope = Context.root().makeCurrent();
  }

  public void onResume() {
    if (this.scope != null) {
      this.scope.close();
    }

    this.scope = this.context.makeCurrent();
  }
}
