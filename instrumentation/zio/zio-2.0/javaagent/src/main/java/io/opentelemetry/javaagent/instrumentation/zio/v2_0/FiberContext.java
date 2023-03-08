/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0;

import io.opentelemetry.context.Context;

public class FiberContext {

  private Context context;

  private FiberContext(Context context) {
    this.context = context;
  }

  public static FiberContext create() {
    return new FiberContext(Context.current());
  }

  public void onEnd() {
    Context.root().makeCurrent();
  }

  public void onSuspend() {
    this.context = Context.current();
    Context.root().makeCurrent();
  }

  public void onResume() {
    this.context.makeCurrent();
  }
}
