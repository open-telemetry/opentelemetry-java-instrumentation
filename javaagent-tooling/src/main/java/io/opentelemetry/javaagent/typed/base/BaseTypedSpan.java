/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.typed.base;

import io.opentelemetry.api.trace.Span;

public abstract class BaseTypedSpan<T extends BaseTypedSpan> extends DelegatingSpan {

  public BaseTypedSpan(Span delegate) {
    super(delegate);
  }

  public void end(Throwable throwable) {
    // add error details to the span.
    super.end();
  }

  /** The end(Throwable), or end(RESPONSE) methods should be used instead. */
  @Deprecated
  @Override
  public void end() {
    super.end();
  }

  /** The end(Throwable), or end(RESPONSE) methods should be used instead. */
  @Deprecated
  @Override
  public void end(long timestamp) {
    super.end(timestamp);
  }

  protected abstract T self();
}
