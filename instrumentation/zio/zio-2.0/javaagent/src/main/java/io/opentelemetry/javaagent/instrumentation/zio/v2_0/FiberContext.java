/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;

public class FiberContext {

  private Span span;
  @Nullable private Scope scope;

  private FiberContext(Span span) {
    this.span = span;
  }

  public static FiberContext create() {
    return new FiberContext(Span.current());
  }

  public void onEnd() {
    if (this.scope != null) {
      this.scope.close();
    }
  }

  public void onSuspend() {
    this.span = Span.current();
    if (this.scope != null) {
      this.scope.close();
    }
  }

  public void onResume() {
    this.scope = this.span.makeCurrent();
  }
}
