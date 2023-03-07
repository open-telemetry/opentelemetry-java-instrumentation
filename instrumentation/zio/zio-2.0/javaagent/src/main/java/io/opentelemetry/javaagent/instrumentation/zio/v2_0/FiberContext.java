/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

public class FiberContext {

  private Span span;

  private FiberContext(Span span) {
    this.span = span;
  }

  public static FiberContext create() {
    return new FiberContext(Span.current());
  }

  public void onEnd() {
    Context.root().makeCurrent();
  }

  public void onSuspend() {
    this.span = Span.current();
    Context.root().makeCurrent();
  }

  public void onResume() {
    this.span.makeCurrent();
  }
}
