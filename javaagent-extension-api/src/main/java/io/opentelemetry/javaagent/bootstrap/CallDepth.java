/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

/**
 * A utility to track nested calls in an instrumentation.
 *
 * <p>For example, this can be used to track nested calls to {@code super()} in constructors by
 * calling {@link #getAndIncrement()} at the beginning of each constructor.
 *
 * <p>This works the following way: when you enter some method that you want to track, you call
 * {@link #getAndIncrement()} method. If returned number is larger than 0, then you have already
 * been in this method and are in recursive call now. When you then leave the method, you call
 * {@link #decrementAndGet()} method. If returned number is larger than 0, then you have already
 * been in this method and are in recursive call now.
 *
 * <p>In short, the semantic of both methods is the same: they will return value 0 if and only if
 * current method invocation is the first one for the current call stack.
 */
public final class CallDepth {

  private int depth;

  CallDepth() {
    this.depth = 0;
  }

  /**
   * Return the current call depth for a given class (not method; we want to be able to track calls
   * between different methods in a class).
   *
   * <p>The returned instance is unique per given class and per thread.
   */
  public static CallDepth forClass(Class<?> cls) {
    return CallDepthThreadLocalMap.getCallDepth(cls);
  }

  /**
   * Increment the current call depth and return the previous value. This method will always return
   * 0 if it's the first (outermost) call.
   */
  public int getAndIncrement() {
    return this.depth++;
  }

  /**
   * Decrement the current call depth and return the current value. This method will always return 0
   * if it's the last (outermost) call.
   */
  public int decrementAndGet() {
    return --this.depth;
  }
}
