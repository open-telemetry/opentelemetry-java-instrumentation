/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api;

public final class CallDepth {
  private int depth;

  CallDepth() {
    this.depth = 0;
  }

  public int getAndIncrement() {
    return this.depth++;
  }

  public int decrementAndGet() {
    return --this.depth;
  }

  void reset() {
    depth = 0;
  }
}
