/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

/**
 * Thread-local suppression with acquisition ownership.
 *
 * <p>A caller that successfully acquires suppression must release it. A caller whose acquisition
 * fails does not own the suppression and must not release it.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ScopedThreadSuppression {

  @SuppressWarnings("ThreadLocalUsage")
  private final ThreadLocal<Boolean> threadLocal = new ThreadLocal<>();

  public boolean tryAcquire() {
    if (isActive()) {
      return false;
    }
    threadLocal.set(Boolean.TRUE);
    return true;
  }

  public boolean isActive() {
    return Boolean.TRUE.equals(threadLocal.get());
  }

  public void release() {
    threadLocal.remove();
  }
}
