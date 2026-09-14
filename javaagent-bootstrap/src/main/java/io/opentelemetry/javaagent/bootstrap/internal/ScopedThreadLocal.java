/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import javax.annotation.Nullable;

/**
 * Thread-local state for a lexical scope.
 *
 * <p>Each call to {@link #set} must be paired with {@link #restore} using the value returned by
 * {@code set}, including when the scoped operation throws.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ScopedThreadLocal<T> {

  @SuppressWarnings("ThreadLocalUsage")
  private final ThreadLocal<T> threadLocal = new ThreadLocal<>();

  @Nullable
  public T get() {
    return threadLocal.get();
  }

  /**
   * Sets the current value and returns the previous value to pass to {@link #restore} when the
   * scope ends.
   */
  @Nullable
  public T set(@Nullable T value) {
    T previous = threadLocal.get();
    update(value);
    return previous;
  }

  /** Restores the value returned by the matching call to {@link #set}. */
  public void restore(@Nullable T previous) {
    update(previous);
  }

  private void update(@Nullable T value) {
    if (value == null) {
      threadLocal.remove();
    } else {
      threadLocal.set(value);
    }
  }
}
